/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
 
CREATE TABLE study.SampleRequestStatus
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    SortOrder INT NULL,
    Label NVARCHAR(100),
    CONSTRAINT PK_SampleRequestStatus PRIMARY KEY (RowId)
)

CREATE TABLE study.SampleRequestActor
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    SortOrder INT NULL,
    Label NVARCHAR(100),
    CONSTRAINT PK_SampleRequestActor PRIMARY KEY (RowId)
)

CREATE TABLE study.SampleRequest
(
    -- standard fields
    _ts TIMESTAMP,
    RowId INT IDENTITY(1,1),
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,
    Container ENTITYID NOT NULL,

    StatusId INT NOT NULL,
    Comments NTEXT,

    CONSTRAINT PK_SampleRequest PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequest_SampleRequestStatus FOREIGN KEY (StatusId) REFERENCES study.SampleRequestStatus(RowId)
)

CREATE TABLE study.SampleRequestLDMSSample
(
    RowId INT IDENTITY(1,1),
    SampleRequestId INT NOT NULL,
    LDMSSampleId INT NOT NULL,

    CONSTRAINT PK_SampleRequestLDMSSample PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestLDMSSample_SampleRequest FOREIGN KEY (SampleRequestId) REFERENCES study.SampleRequest(RowId),
    CONSTRAINT FK_SampleRequestLDMSSample_LDMSSample FOREIGN KEY (LDMSSampleId) REFERENCES study.LDMSSample(RowId)
)

CREATE TABLE study.SampleRequestRequirement
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    RequestId INT NOT NULL,
    ActorId INT NOT NULL,
    SiteId INT NULL,
    Description NVARCHAR(300),
    Complete Bit NOT NULL DEFAULT 0,

    CONSTRAINT PK_SampleRequestRequirement PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestRequirement_SampleRequest FOREIGN KEY (RequestId) REFERENCES study.SampleRequest(RowId),
    CONSTRAINT FK_SampleRequestRequirement_SampleRequestActor FOREIGN KEY (ActorId) REFERENCES study.SampleRequestActor(RowId),
    CONSTRAINT FK_SampleRequestRequirement_Site FOREIGN KEY (Container,SiteId) REFERENCES study.Site(Container,SiteId)
)

ALTER TABLE study.LDMSSampleEvent
   ADD RowId INT IDENTITY(1,1);

ALTER TABLE study.LDMSSampleEvent
   ADD CONSTRAINT PK_LDMSSampleEvent PRIMARY KEY (RowId);