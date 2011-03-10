/*
 * Copyright (c) 2011 LabKey Corporation
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

/* study-10.30-10.31.sql */

EXEC sp_addapprole 'assayresult', 'password'
GO

EXEC core.executeJavaUpgradeCode 'materializeAssayResults'
GO

/* study-10.31-10.32.sql */

EXEC core.executeJavaUpgradeCode 'deleteDuplicateAssayDatasetFields'
GO

/* study-10.32-10.33.sql */

ALTER TABLE study.SpecimenEvent ADD TotalCellCount INT
GO

ALTER TABLE study.Vial ADD TotalCellCount INT
GO