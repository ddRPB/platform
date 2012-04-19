/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

-- populate the view category table with the dataset categories
INSERT INTO core.ViewCategory (Container, Label, CreatedBy, ModifiedBy)
  SELECT Container, Category, 0, 0 FROM study.Dataset WHERE LENGTH(Category) > 0 GROUP BY Container, Category;

ALTER TABLE study.Dataset ADD COLUMN CategoryId Integer;

UPDATE study.Dataset ds
    SET CategoryId = (SELECT rowId FROM core.ViewCategory vc WHERE ds.container = vc.container AND ds.category = vc.label);

-- drop the category column
ALTER TABLE study.Dataset DROP COLUMN Category;