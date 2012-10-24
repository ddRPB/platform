/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.gwt.client;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 10/17/12
 */
public enum AuditBehaviorType
{
    NONE("None"),
    DETAILED("Detailed"),
    SUMMARY("Summary");

    private String _label;

    AuditBehaviorType(String label)
    {
        _label = label;
    }

    public String getLabel()
    {
        return _label;
    }
}
