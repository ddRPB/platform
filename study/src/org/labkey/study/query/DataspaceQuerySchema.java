/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.study.query;

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.security.User;
import org.labkey.study.model.StudyImpl;

/**
 * Created by matthew on 2/11/14.
 *
 * Don't really need a subclass, but this helps isolate the exact difference between a regular study schema, and
 * a dataspace study schema.
 */

public class DataspaceQuerySchema extends StudyQuerySchema
{
    public DataspaceQuerySchema(StudyImpl study, User user, boolean mustCheckPermissions)
    {
        super(study, user, mustCheckPermissions);
    }

    /* for tables that support container filter, should they turn on support or not */
    @Override
    public boolean allowSetContainerFilter()
    {
        return false;
    }


    @Override
    ContainerFilter getDefaultContainerFilter()
    {
        return new DataspaceContainerFilter(getUser());
    }


    @Override
    public boolean isDataspace()
    {
        return true;
    }
}
