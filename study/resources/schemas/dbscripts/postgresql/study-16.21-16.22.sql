/*
 * Copyright (c) 2016 LabKey Corporation
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
CREATE TABLE study.DoseAndRoute
(
  RowId SERIAL,
  Label VARCHAR(600),
  Dose VARCHAR(200),
  Route VARCHAR(200),
  ProductId INT NOT NULL,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_DoseAndRoute PRIMARY KEY (RowId),
  CONSTRAINT DoseAndRoute_Dose_Route_ProductId UNIQUE (Dose, Route, ProductId)
);

SELECT core.executeJavaUpgradeCode('populateDoseAndRoute');
