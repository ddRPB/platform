/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.VirtualFile;
import org.springframework.validation.Errors;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Base class for auto-created custom views that are based on column-level metadata about visibility. These views are
 * intended for client API usage, are present for all TableInfos/queries, and are hidden so they don't clutter the
 * lists we typically show to users.
 *
 * They're effectively read-only.
 *
 * User: jeckels
 * Date: 11/21/12
 */
public abstract class AutoGeneratedCustomView implements CustomView
{
    private final @NotNull QueryDefinition _queryDef;
    private final @NotNull String _name;
    private List<FieldKey> _columns;

    public AutoGeneratedCustomView(@NotNull QueryDefinition queryDef, @NotNull String name)
    {
        _queryDef = queryDef;
        _name = name;
    }

    @Override
    public QueryDefinition getQueryDefinition()
    {
        return _queryDef;
    }

    @Override
    public void setName(String name)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setQueryName(String queryName)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCanInherit(boolean f)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canEdit(Container c, Errors errors)
    {
        return false;
    }

    @Override
    public void setIsHidden(boolean f)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setColumns(List<FieldKey> columns)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setColumnProperties(List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> list)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void applyFilterAndSortToURL(ActionURL url, String dataRegionName)
    {

    }

    @Override
    public void setFilterAndSortFromURL(ActionURL url, String dataRegionName)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFilterAndSort(String filter)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void save(User user, HttpServletRequest request) throws QueryException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete(User user, HttpServletRequest request) throws QueryException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean serialize(VirtualFile dir) throws IOException
    {
        return true;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public String getLabel()
    {
        return _name;
    }

    @Override
    public User getOwner()
    {
        return null;
    }

    @Override
    public boolean isShared()
    {
        return true;
    }

    @Override
    public User getCreatedBy()
    {
        return null;
    }

    @Override
    public Date getCreated()
    {
        return new Date();
    }

    @Override
    public User getModifiedBy()
    {
        return null;
    }

    @Override
    public Date getModified()
    {
        return new Date();
    }

    @Override
    public String getSchemaName()
    {
        return _queryDef.getSchema().getSchemaName();
    }

    @Override
    public SchemaKey getSchemaPath()
    {
        return _queryDef.getSchema().getSchemaPath();
    }

    @Override
    public String getQueryName()
    {
        return _queryDef.getName();
    }

    @Override
    public Container getContainer()
    {
        return null;
    }

    @Override
    public String getEntityId()
    {
        return null;
    }

    @Override
    public boolean canInherit()
    {
        return false;
    }

    /** @return true so that this view doesn't clutter up the user's lists */
    @Override
    public boolean isHidden()
    {
        return true;
    }

    @Override
    public boolean isEditable()
    {
        return false;
    }

    @Override
    public boolean isDeletable()
    {
        return false;
    }

    @Override
    public boolean isRevertable()
    {
        return false;
    }

    @Override
    public boolean isOverridable()
    {
        return true;
    }

    @Override
    public boolean isSession()
    {
        return false;
    }

    @Override
    public String getCustomIconUrl()
    {
        return null;
    }

    @Override
    public String getCustomIconCls()
    {
        return null;
    }

    @NotNull
    @Override
    public List<FieldKey> getColumns()
    {
        if (_columns == null)
        {
            _columns = new ArrayList<>();
            TableInfo table = _queryDef.getTable(new ArrayList<QueryException>(), true);
            if (table != null)
            {
                _columns = getFieldKeysToShow(table);
            }
        }
        return _columns;
    }

    protected abstract List<FieldKey> getFieldKeysToShow(TableInfo table);

    @NotNull
    @Override
    public List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> getColumnProperties()
    {
        List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> result = new ArrayList<>();
        for (FieldKey fieldKey : getColumns())
        {
            result.add(new Pair<>(fieldKey, Collections.emptyMap()));
        }
        return result;
    }

    @Override
    public String getFilterAndSort()
    {
        return null;
    }

    @Override
    public String getContainerFilterName()
    {
        return ContainerFilter.Type.Current.name();
    }

    @Override
    public boolean hasFilterOrSort()
    {
        return false;
    }

    @Override
    public Collection<String> getDependents(User user)
    {
        return Collections.emptyList();
    }

}
