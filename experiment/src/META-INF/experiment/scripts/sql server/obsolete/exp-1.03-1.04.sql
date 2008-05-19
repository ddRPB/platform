/*
 * Copyright (c) 2005-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


CREATE VIEW exp.AllLsidContainers AS
	SELECT LSID, Container, 'Protocol' AS Type FROM exp.Protocol UNION ALL
	SELECT exp.ProtocolApplication.LSID, Container, 'ProtocolApplication' AS Type FROM exp.ProtocolApplication JOIN exp.Protocol ON exp.Protocol.LSID = exp.ProtocolApplication.ProtocolLSID UNION ALL
	SELECT LSID, Container, 'Experiment' AS Type FROM exp.Experiment UNION ALL
	SELECT LSID, Container, 'Material' AS Type FROM exp.Material UNION ALL
	SELECT LSID, Container, 'MaterialSource' AS Type FROM exp.MaterialSource UNION ALL
	SELECT LSID, Container, 'Data' AS Type FROM exp.Data UNION ALL
	SELECT LSID, Container, 'ExperimentRun' AS Type FROM exp.ExperimentRun
GO


--
-- PropertyDescriptor
--


-- TODO: use M-M junction table for PropertyDescriptor <-> ObjectType
-- I'd like to rename RowId to PropertyId

ALTER TABLE exp.PropertyDescriptor ADD DatatypeURI nvarchar(200) NOT NULL DEFAULT 'http://www.w3.org/2001/XMLSchema#string'
go


--
-- Object
--

-- DROP TABLE exp.ObjectProperty
-- DROP TABLE exp.Object

CREATE TABLE exp.Object
	(
	ObjectId int IDENTITY(1,1) NOT NULL,
	Container ENTITYID NOT NULL,
	ObjectURI LSIDType NOT NULL,
	OwnerObjectId int NULL
	)
go


ALTER TABLE exp.Object ADD CONSTRAINT PK_Object PRIMARY KEY NONCLUSTERED (ObjectId);
ALTER TABLE exp.Object ADD CONSTRAINT UQ_Object UNIQUE CLUSTERED (Container, ObjectURI);
CREATE INDEX IDX_Object_OwnerObjectId ON exp.Object (OwnerObjectId);
ALTER TABLE exp.Object ADD
	CONSTRAINT FK_Object_Object FOREIGN KEY (OwnerObjectId)
		REFERENCES exp.Object (ObjectId)
go

--
-- ObjectProperty
--


CREATE TABLE exp.ObjectProperty
	(
	ObjectId int NOT NULL,  -- FK exp.Object
	PropertyId int NOT NULL, -- FK exp.PropertyDescriptor
	TypeTag char(1) NOT NULL, -- s string, f float, d datetime, t text
	FloatValue float NULL,
	DateTimeValue datetime NULL,
	StringValue nvarchar(400) NULL,
	TextValue ntext NULL
	)
go


ALTER TABLE exp.ObjectProperty
   ADD CONSTRAINT PK_ObjectProperty PRIMARY KEY CLUSTERED (ObjectId, PropertyId);
ALTER TABLE exp.ObjectProperty ADD
	CONSTRAINT FK_ObjectProperty_Object FOREIGN KEY (ObjectId)
		REFERENCES exp.Object (ObjectId);
ALTER TABLE exp.ObjectProperty ADD
	CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId)
		REFERENCES exp.PropertyDescriptor (RowId)
go



-- DROP PROCEDURE exp.getObjectProperties

CREATE VIEW exp.ObjectPropertiesView AS
	SELECT
		O.*,
		PD.name, PD.PropertyURI, PD.DatatypeURI,
		P.TypeTag, P.FloatValue, P.StringValue, P.DatetimeValue, P.TextValue
	FROM exp.ObjectProperty P JOIN exp.Object O ON P.ObjectId = O.ObjectId JOIN exp.PropertyDescriptor PD ON P.PropertyID = PD.RowId
go



CREATE PROCEDURE exp.getObjectProperties(@container ENTITYID, @lsid LSIDType) AS
BEGIN
	SELECT * FROM exp.ObjectPropertiesView
	WHERE Container = @container AND ObjectURI = @lsid
END
go



-- DROP PROCEDURE exp.ensureObject

CREATE PROCEDURE exp.ensureObject(@container ENTITYID, @lsid LSIDType, @ownerObjectId INTEGER) AS
BEGIN
	DECLARE @objectid AS INTEGER
	SET NOCOUNT ON
	BEGIN TRANSACTION
		SELECT @objectid = ObjectId FROM exp.Object where Container=@container AND ObjectURI=@lsid
		IF (@objectid IS NULL)
		BEGIN
			INSERT INTO exp.Object (Container, ObjectURI, OwnerObjectId) VALUES (@container, @lsid, @ownerObjectId)
			SELECT @objectid = @@identity
		END
	COMMIT
	SELECT @objectid
END
go


CREATE PROCEDURE exp.deleteObject(@container ENTITYID, @lsid LSIDType) AS
BEGIN
    SET NOCOUNT ON
		DECLARE @objectid INTEGER
		SELECT @objectid = ObjectId FROM exp.Object where Container=@container AND ObjectURI=@lsid
		if (@objectid IS NULL)
			RETURN
    BEGIN TRANSACTION
		DELETE exp.ObjectProperty WHERE ObjectId IN
			(SELECT ObjectId FROM exp.Object WHERE OwnerObjectId = @objectid)
		DELETE exp.ObjectProperty WHERE ObjectId = @objectid
		DELETE exp.Object WHERE OwnerObjectId = @objectid
		DELETE exp.Object WHERE ObjectId = @objectid
	COMMIT
END
go



--
-- This is the most general set property method
--

CREATE PROCEDURE exp.setProperty(@objectid INTEGER, @propertyuri LSIDType, @datatypeuri LSIDType,
	@tag CHAR(1), @f FLOAT, @s NVARCHAR(400), @d DATETIME, @t NTEXT) AS
BEGIN
	DECLARE @propertyid INTEGER
	SET NOCOUNT ON
	BEGIN TRANSACTION
		SELECT @propertyid = RowId FROM exp.PropertyDescriptor WHERE PropertyURI=@propertyuri
		if (@propertyid IS NULL)
			BEGIN
			INSERT INTO exp.PropertyDescriptor (PropertyURI, DatatypeURI) VALUES (@propertyuri, @datatypeuri)
			SELECT @propertyid = @@identity
			END
		DELETE exp.ObjectProperty WHERE ObjectId=@objectid AND PropertyId=@propertyid
		INSERT INTO exp.ObjectProperty (ObjectId, PropertyID, TypeTag, FloatValue, StringValue, DateTimeValue, TextValue)
			VALUES (@objectid, @propertyid, @tag, @f, @s, @d, @t)
	COMMIT
END
go


-- internal methods
CREATE PROCEDURE exp._insertFloatProperty(@objectid INTEGER, @propid INTEGER, @float FLOAT) AS
BEGIN
	IF (@propid IS NULL OR @objectid IS NULL) RETURN
	INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, FloatValue)
	VALUES (@objectid, @propid, 'f', @float)
END
go


CREATE PROCEDURE exp._insertDateTimeProperty(@objectid INTEGER, @propid INTEGER, @datetime DATETIME) AS
BEGIN
	IF (@propid IS NULL OR @objectid IS NULL) RETURN
	INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, DateTimeValue)
	VALUES (@objectid, @propid, 'd', @datetime)
END
go

CREATE PROCEDURE exp._insertStringProperty(@objectid INTEGER, @propid INTEGER, @string VARCHAR(400)) AS
BEGIN
	IF (@propid IS NULL OR @objectid IS NULL) RETURN
	INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, StringValue)
	VALUES (@objectid, @propid, 's', @string)
END
go



--
-- Set the same property on multiple objects (e.g. impoirt a column of datea)
--
-- fast method for importing ObjectProperties (need to wrap with datalayer code)
--


CREATE PROCEDURE exp.setFloatProperties(@propertyid INTEGER,
	@objectid1 INTEGER, @float1 FLOAT,
	@objectid2 INTEGER, @float2 FLOAT,
	@objectid3 INTEGER, @float3 FLOAT,
	@objectid4 INTEGER, @float4 FLOAT,
	@objectid5 INTEGER, @float5 FLOAT,
	@objectid6 INTEGER, @float6 FLOAT,
	@objectid7 INTEGER, @float7 FLOAT,
	@objectid8 INTEGER, @float8 FLOAT,
	@objectid9 INTEGER, @float9 FLOAT,
	@objectid10 INTEGER, @float10 FLOAT
	) AS
BEGIN
	SET NOCOUNT ON
	BEGIN TRANSACTION
		DELETE exp.ObjectProperty WHERE PropertyId=@propertyid AND ObjectId IN (@objectid1, @objectid2, @objectid3, @objectid4, @objectid5, @objectid6, @objectid7, @objectid8, @objectid9, @objectid10)
		EXEC exp._insertFloatProperty @objectid1, @propertyid, @float1
		EXEC exp._insertFloatProperty @objectid2, @propertyid, @float2
		EXEC exp._insertFloatProperty @objectid3, @propertyid, @float3
		EXEC exp._insertFloatProperty @objectid4, @propertyid, @float4
		EXEC exp._insertFloatProperty @objectid5, @propertyid, @float5
		EXEC exp._insertFloatProperty @objectid6, @propertyid, @float6
		EXEC exp._insertFloatProperty @objectid7, @propertyid, @float7
		EXEC exp._insertFloatProperty @objectid8, @propertyid, @float8
		EXEC exp._insertFloatProperty @objectid9, @propertyid, @float9
		EXEC exp._insertFloatProperty @objectid10, @propertyid, @float10
	COMMIT
END
go


CREATE PROCEDURE exp.setStringProperties(@propertyid INTEGER,
	@objectid1 INTEGER, @string1 VARCHAR(400),
	@objectid2 INTEGER, @string2 VARCHAR(400),
	@objectid3 INTEGER, @string3 VARCHAR(400),
	@objectid4 INTEGER, @string4 VARCHAR(400),
	@objectid5 INTEGER, @string5 VARCHAR(400),
	@objectid6 INTEGER, @string6 VARCHAR(400),
	@objectid7 INTEGER, @string7 VARCHAR(400),
	@objectid8 INTEGER, @string8 VARCHAR(400),
	@objectid9 INTEGER, @string9 VARCHAR(400),
	@objectid10 INTEGER, @string10 VARCHAR(400)
	) AS
BEGIN
	SET NOCOUNT ON
	BEGIN TRANSACTION
		DELETE exp.ObjectProperty WHERE PropertyId=@propertyid AND ObjectId IN (@objectid1, @objectid2, @objectid3, @objectid4, @objectid5, @objectid6, @objectid7, @objectid8, @objectid9, @objectid10)
		EXEC exp._insertStringProperty @objectid1, @propertyid, @string1
		EXEC exp._insertStringProperty @objectid2, @propertyid, @string2
		EXEC exp._insertStringProperty @objectid3, @propertyid, @string3
		EXEC exp._insertStringProperty @objectid4, @propertyid, @string4
		EXEC exp._insertStringProperty @objectid5, @propertyid, @string5
		EXEC exp._insertStringProperty @objectid6, @propertyid, @string6
		EXEC exp._insertStringProperty @objectid7, @propertyid, @string7
		EXEC exp._insertStringProperty @objectid8, @propertyid, @string8
		EXEC exp._insertStringProperty @objectid9, @propertyid, @string9
		EXEC exp._insertStringProperty @objectid10, @propertyid, @string10
	COMMIT
END
go


CREATE PROCEDURE exp.setDateTimeProperties(@propertyid INTEGER,
	@objectid1 INTEGER, @datetime1 DATETIME,
	@objectid2 INTEGER, @datetime2 DATETIME,
	@objectid3 INTEGER, @datetime3 DATETIME,
	@objectid4 INTEGER, @datetime4 DATETIME,
	@objectid5 INTEGER, @datetime5 DATETIME,
	@objectid6 INTEGER, @datetime6 DATETIME,
	@objectid7 INTEGER, @datetime7 DATETIME,
	@objectid8 INTEGER, @datetime8 DATETIME,
	@objectid9 INTEGER, @datetime9 DATETIME,
	@objectid10 INTEGER, @datetime10 DATETIME
	) AS
BEGIN
	SET NOCOUNT ON
	BEGIN TRANSACTION
		DELETE exp.ObjectProperty WHERE PropertyId=@propertyid AND ObjectId IN (@objectid1, @objectid2, @objectid3, @objectid4, @objectid5, @objectid6, @objectid7, @objectid8, @objectid9, @objectid10)
		EXEC exp._insertDateTimeProperty @objectid1, @propertyid, @datetime1
		EXEC exp._insertDateTimeProperty @objectid2, @propertyid, @datetime2
		EXEC exp._insertDateTimeProperty @objectid3, @propertyid, @datetime3
		EXEC exp._insertDateTimeProperty @objectid4, @propertyid, @datetime4
		EXEC exp._insertDateTimeProperty @objectid5, @propertyid, @datetime5
		EXEC exp._insertDateTimeProperty @objectid6, @propertyid, @datetime6
		EXEC exp._insertDateTimeProperty @objectid7, @propertyid, @datetime7
		EXEC exp._insertDateTimeProperty @objectid8, @propertyid, @datetime8
		EXEC exp._insertDateTimeProperty @objectid9, @propertyid, @datetime9
		EXEC exp._insertDateTimeProperty @objectid10, @propertyid, @datetime10
	COMMIT
END
go



--
-- Migrate data
--


-- Experiment Objects (need to fix up container)
BEGIN TRAN

INSERT INTO exp.Object (Container, ObjectURI)
SELECT DISTINCT '00000000-0000-0000-0000-000000000000', ParentURI
FROM exp.Property


-- PropertyDescriptors


INSERT INTO exp.PropertyDescriptor (PropertyURI, ValueType, Name)
SELECT OntologyEntryURI, MAX(ValueType), MAX(Name)
FROM exp.Property
WHERE OntologyEntryURI NOT IN (SELECT PropertyURI FROM exp.PropertyDescriptor)
GROUP BY OntologyEntryURI

UPDATE exp.PropertyDescriptor SET DatatypeURI =
	CASE
		WHEN ValueType = 'String'      THEN 'http://www.w3.org/2001/XMLSchema#string'
		WHEN ValueType = 'PropertyURI' THEN 'http://www.w3.org/2000/01/rdf-schema#Resource'
		WHEN ValueType = 'Integer'     THEN 'http://www.w3.org/2001/XMLSchema#int'
		WHEN ValueType = 'FileLink'    THEN 'http://cpas.fhcrc.org/exp/xml#fileLink'
		WHEN ValueType = 'DateTime'    THEN 'http://www.w3.org/2001/XMLSchema#dateTime'
		WHEN ValueType = 'Double'      THEN 'http://www.w3.org/2001/XMLSchema#double'
		WHEN ValueType = 'XmlText'     THEN 'http://cpas.fhcrc.org/exp/document#text-xml'
	END
WHERE ValueType IN ('String', 'PropertyURI', 'Integer', 'FileLink', 'DateTime', 'Double', 'XmlText')


-- Properties

INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, FloatValue, DateTimeValue, StringValue, TextValue)
SELECT O.ObjectId, PD.RowId,
	CASE
		WHEN P.ValueType = 'String' THEN 's'
		WHEN P.ValueType = 'PropertyURI' THEN 's'
		WHEN P.ValueType = 'Integer' THEN 'f'
		WHEN P.ValueType = 'FileLink' THEN 's'
		WHEN P.ValueType = 'DateTime' THEN 'd'
		WHEN P.ValueType = 'Double' THEN 'f'
		WHEN P.ValueType = 'XmlText' THEN 't'
	END,
	ISNULL(IntegerValue,DoubleValue), -- Float
	DateTimeValue, -- DateTime
	ISNULL(StringValue,ISNULL(PropertyURIValue,FileLinkValue)), -- String
	XmlTextValue -- Text
FROM exp.Property P INNER JOIN exp.Object O on P.ParentURI = O.ObjectURI INNER JOIN exp.PropertyDescriptor PD on P.OntologyEntryURI = PD.PropertyURI


--
-- fix-up Containers in the Objects table
--

-- find object with same LSID and update container
UPDATE exp.Object
SET Container = L.Container 
FROM exp.Object INNER JOIN exp.AllLsidContainers L ON exp.Object.ObjectURI = L.Lsid
WHERE exp.Object.Container = '00000000-0000-0000-0000-000000000000'
print @@rowcount

-- find children and udpate container
UPDATE exp.Object SET Container = Parent.Container
FROM exp.Object INNER JOIN exp.ObjectProperty OP ON exp.Object.ObjectURI = OP.StringValue INNER JOIN exp.Object Parent ON OP.ObjectId = Parent.ObjectID
WHERE exp.Object.Container = '00000000-0000-0000-0000-000000000000'
print @@rowcount

-- repeat
UPDATE exp.Object SET Container = Parent.Container
FROM exp.Object INNER JOIN exp.ObjectProperty OP ON exp.Object.ObjectURI = OP.StringValue INNER JOIN exp.Object Parent ON OP.ObjectId = Parent.ObjectID
WHERE exp.Object.Container = '00000000-0000-0000-0000-000000000000'
print @@rowcount


SELECT COUNT(*)
FROM exp.Object
WHERE Container = '00000000-0000-0000-0000-000000000000'

--
-- fix up OwnerObjectId
--

UPDATE exp.Object
SET OwnerObjectId = (SELECT MAX(OWNER.ObjectId)
		FROM exp.Property PROPS JOIN exp.ExperimentRun RUN on PROPS.RunId = RUN.RowId JOIN exp.Object OWNER ON RUN.Lsid = OWNER.ObjectURI
		WHERE PROPS.ParentURI = exp.Object.ObjectURI)

COMMIT TRAN
go
