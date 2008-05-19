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
ALTER TABLE study.Participant
    DROP CONSTRAINT PK_Participant
GO

DROP INDEX study.Participant.IX_Participant_ParticipantId
GO    

ALTER TABLE study.Participant
    ALTER COLUMN ParticipantId NVARCHAR(32) NOT NULL
GO

ALTER TABLE study.Participant
    ADD CONSTRAINT PK_Participant PRIMARY KEY (Container, ParticipantId)
GO    

CREATE INDEX IX_Participant_ParticipantId ON study.Participant(ParticipantId)
GO    