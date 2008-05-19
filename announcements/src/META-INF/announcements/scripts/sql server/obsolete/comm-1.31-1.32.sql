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


CREATE TABLE comm.EmailOptions
	(
	EmailOptionId INT NOT NULL,
	EmailOption NVARCHAR(50),
	CONSTRAINT PK_EmailOptions PRIMARY KEY (EmailOptionId)
	)

INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption)
VALUES (0, 'No Email')

INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption)
VALUES (1, 'All messages')

INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption)
VALUES (2, 'Posted threads only')


CREATE TABLE comm.EmailFormats
	(
	EmailFormatId INT NOT NULL,
	EmailFormat NVARCHAR(20),
	CONSTRAINT PK_EmailFormats PRIMARY KEY (EmailFormatId)
	)

INSERT INTO comm.EmailFormats (EmailFormatId, EmailFormat)
VALUES (0, 'Plain Text')

INSERT INTO comm.EmailFormats (EmailFormatId, EmailFormat)
VALUES (1, 'HTML')


CREATE TABLE comm.PageTypes
	(
	PageTypeId INT NOT NULL,
	PageType NVARCHAR(20),
	CONSTRAINT PK_PageTypes PRIMARY KEY (PageTypeId)
	)

INSERT INTO comm.PageTypes (PageTypeId, PageType)
VALUES (0, 'Message')

INSERT INTO comm.PageTypes (PageTypeId, PageType)
VALUES (1, 'Wiki')

CREATE TABLE comm.EmailPrefs
	(
	Container ENTITYID,
	UserId USERID,
	EmailOptionId INT NOT NULL,
	EmailFormatId INT NOT NULL,
	PageTypeId INT NOT NULL,
	CONSTRAINT PK_EmailPrefs PRIMARY KEY (Container, UserId),
	CONSTRAINT FK_EmailPrefs_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
	CONSTRAINT FK_EmailPrefs_Principals FOREIGN KEY (UserId) REFERENCES core.Principals (UserId),
	CONSTRAINT FK_EmailPrefs_EmailOptions FOREIGN KEY (EmailOptionId) REFERENCES comm.EmailOptions (EmailOptionId),
	CONSTRAINT FK_EmailPrefs_EmailFormats FOREIGN KEY (EmailFormatId) REFERENCES comm.EmailFormats (EmailFormatId),
	CONSTRAINT FK_EmailPrefs_PageTypes FOREIGN KEY (PageTypeId) REFERENCES comm.PageTypes (PageTypeId)
	)


INSERT INTO comm.EmailPrefs (Container, UserId, EmailOptionId, EmailFormatId, PageTypeId)
SELECT     core.Containers.EntityId AS Container, prop.PropertySets.UserId, prop.Properties.Value, 1, 1
FROM         prop.Properties INNER JOIN
                      prop.PropertySets ON prop.Properties.[Set] = prop.PropertySets.[Set] INNER JOIN
                      core.Containers ON prop.PropertySets.ObjectId = core.Containers.EntityId INNER JOIN
                      core.Users on prop.PropertySets.UserId = core.Users.UserId
WHERE     (prop.PropertySets.Category = 'Announcements' AND prop.Properties.Name = 'email')

DELETE FROM prop.Properties
WHERE prop.Properties.[Set] IN
    (SELECT [Set] from prop.PropertySets WHERE prop.PropertySets.Category = 'Announcements')

DELETE FROM prop.PropertySets WHERE prop.PropertySets.Category = 'Announcements'
