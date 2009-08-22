/*
 * Copyright (c) 2009 LabKey Corporation
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

package org.labkey.api.query;

import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Aug 3, 2009
 */
public interface ViewOptions
{
    List<ViewFilterItem> getViewFilterItems();
    void setViewFilterItems(List<ViewFilterItem> items);

    void save(User user) throws SQLException;
    void delete(User user) throws SQLException;

    public interface ViewFilterItem
    {
        String getViewType();
        boolean isEnabled();
    }
}
