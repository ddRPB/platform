/*
 * Copyright (c) 2019 LabKey Corporation
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
 * See the Licensfn_dropifexistse for the specific language governing permissions and
 * limitations under the License.
 */

-- Removing active sample sets
SELECT core.fn_dropifexists('ActiveMaterialSource', 'exp', 'TABLE', NULL);

CREATE SCHEMA IF NOT EXISTS expsampleset;
SELECT core.executeJavaUpgradeCode('materializeSampleSets');

ALTER TABLE exp.materialsource
  ALTER COLUMN nameexpression TYPE VARCHAR(500);

ALTER TABLE exp.dataclass
  ALTER COLUMN nameexpression TYPE VARCHAR(500);

SELECT core.executeJavaUpgradeCode('addSampleSetGenId');

UPDATE exp.data SET datafileurl = 'file:///' || substr(datafileurl, 7) WHERE datafileurl LIKE 'file:/_%' AND datafileurl NOT LIKE 'file:///%' AND datafileurl IS NOT NULL;