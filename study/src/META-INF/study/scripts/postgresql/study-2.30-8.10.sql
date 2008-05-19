/*
 * Copyright (c) 2008 LabKey Corporation
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
/* study-2.30-2.31.sql */

ALTER TABLE study.Plate ADD COLUMN Type VARCHAR(200);

/* study-2.31-2.32.sql */

CREATE TABLE study.Cohort
    (
    RowId SERIAL,
    Label VARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    CONSTRAINT PK_Cohort PRIMARY KEY (RowId),
    CONSTRAINT UQ_Cohort_Label UNIQUE(Label, Container)
    );

ALTER TABLE study.Dataset
    ADD COLUMN CohortId INT NULL,
    ADD CONSTRAINT FK_Dataset_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId);

CREATE INDEX IX_Dataset_CohortId ON study.Dataset(CohortId);

ALTER TABLE study.Participant
    ADD COLUMN CohortId INT NULL,
    ADD CONSTRAINT FK_Participant_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId);

CREATE INDEX IX_Participant_CohortId ON study.Participant(CohortId);
CREATE INDEX IX_Participant_ParticipantId ON study.Participant(ParticipantId);
CREATE INDEX IX_Specimen_Ptid ON study.Specimen(Ptid);

ALTER TABLE study.Visit
    ADD COLUMN CohortId INT NULL,
    ADD CONSTRAINT FK_Visit_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId);

CREATE INDEX IX_Visit_CohortId ON study.Visit(CohortId);

ALTER TABLE study.Study
    ADD COLUMN ParticipantCohortDataSetId INT NULL,
    ADD COLUMN ParticipantCohortProperty VARCHAR(200) NULL;

/* study-2.32-2.33.sql */

ALTER TABLE study.Participant
    ALTER COLUMN ParticipantId TYPE VARCHAR(32);

/* study-2.33-2.34.sql */

ALTER TABLE study.SpecimenEvent
    ALTER COLUMN Comments TYPE VARCHAR(200);