/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.query.controllers;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.*;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.view.ActionURL;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;

import java.util.*;

public class ChooseColumnsForm extends DesignForm
{
    public LinkedHashSet<FieldKey> ff_selectedColumns = new LinkedHashSet<FieldKey>();
    public String ff_columnListName;
    public boolean ff_saveForAllUsers;
    public boolean ff_saveFilter;
    public boolean ff_inheritable;

    private ActionURL _sourceURL;

    public BindException bindParameters(PropertyValues params)
    {
        BindException errors =  super.bindParameters(params);

        //NOTE we want querySettings to be based on srcURL parameters
        // get queryName, viewName and replace _initParameters
        setDataRegionName(getValue(QueryParam.dataRegionName, params));
        setQueryName(getValue(QueryParam.queryName, params));
        setViewName(getValue(QueryParam.viewName, params));
        _initParameters = new MutablePropertyValues();
        String src = getValue(QueryParam.srcURL, params);
        if (src != null)
        {
            _sourceURL = new ActionURL(src);
            _sourceURL.setReadOnly();
            ((MutablePropertyValues)_initParameters).addPropertyValues(_sourceURL.getPropertyValues());
        }

        return errors;
    }

    
    public void initForView()
    {
        if (null == getQuerySettings())
            return;

        ff_columnListName = getQuerySettings().getViewName();
        CustomView cv = getCustomView();
        if (cv != null && cv.getColumns() != null)
        {
            ff_selectedColumns.addAll(cv.getColumns());
            ff_inheritable = cv.canInherit();
        }
        if (ff_selectedColumns.isEmpty())
        {
            TableInfo table = getQueryDef().getTable(getSchema(), null, true);
            if (table != null)
            {
                for (ColumnInfo column : table.getColumns())
                {
                    if (!column.isHidden() && !column.isUnselectable())
                    {
                        ff_selectedColumns.add(new FieldKey(null, column.getName()));
                    }
                }
            }
        }
    }


    public void setFf_selectedColumns(String columns)
    {
        ff_selectedColumns = new LinkedHashSet<FieldKey>();
        if (columns != null)
        {
            for (String column : StringUtils.split(columns, '&'))
            {
                ff_selectedColumns.add(FieldKey.fromString(column));
            }
        }
    }

    public void setFf_saveForAllUsers(boolean b)
    {
        ff_saveForAllUsers = b;
    }

    public void setFf_inheritable(boolean ff_inheritable)
    {
        this.ff_inheritable = ff_inheritable;
    }

    public boolean isCustomViewInherited()
    {
        CustomView customView = getCustomView();
        if (customView != null && customView.canInherit())
        {
            return !getContainer().getId().equals(customView.getContainer().getId());
        }
        return false;
    }

    public void setFf_columnListName(String name)
    {
        ff_columnListName = name;
    }

    private void addColumns(Map<FieldKey, ColumnInfo> map, TableInfo table, FieldKey tableKey, int depth)
    {
        for (String columnName : table.getColumnNameSet())
        {
            ColumnInfo column = table.getColumn(columnName);
            FieldKey fieldKey;
            if (tableKey == null)
            {
                fieldKey = FieldKey.fromString(columnName);
            }
            else
            {
                fieldKey = new FieldKey(tableKey, columnName);
            }
            map.put(fieldKey, column);
            if (depth > 0)
            {
                TableInfo fkTableInfo = column.getFkTableInfo();
                if (fkTableInfo != null)
                {
                    addColumns(map, fkTableInfo, fieldKey, depth - 1);
                }
            }
        }
    }

    public ActionURL urlFor(QueryAction action)
    {
        ActionURL ret = super.urlFor(action);
        if (null != getSourceURL())
            ret.addParameter(QueryParam.srcURL.toString(), getSourceURL().toString());
        ret.addParameter(QueryParam.dataRegionName.toString(), getDataRegionName());
        ret.addParameter(QueryParam.queryName.toString(), getQueryName());
        return ret;
    }

    private Map<FieldKey, ColumnInfo> getAvailableColumns()
    {
        Map<FieldKey, ColumnInfo> ret = new TreeMap<FieldKey, ColumnInfo>();
        TableInfo table = getQueryDef().getTable(getSchema(), null, true);
        addColumns(ret, table, null, 3);

        return QueryService.get().getColumns(table, ret.keySet());

        //return ret;
    }

    protected boolean isFilterOrSort(String dataRegionName, String param)
    {
        assert param.startsWith(dataRegionName + ".");
        String check = param.substring(dataRegionName.length() + 1);
        if (check.indexOf("~") >= 0)
            return true;
        if ("sort".equals(check))
            return true;
        if (check.equals("containerFilterName"))
            return true;
        return false;
    }

    public List<String> getFilterColumnNamesFromURL()
    {
        ActionURL url = getSourceURL();
        if (url == null)
            return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        for (String key : url.getKeysByPrefix(getDataRegionName() + "."))
        {
            String editedKey = key.substring(getDataRegionName().length() + 1);
            int tildeIndex = editedKey.indexOf("~");
            if (tildeIndex >= 0)
            {
                String colName = editedKey.substring(0, tildeIndex);
                result.add(getCaption(colName));
            }
        }
        return result;
    }

    private String getCaption(String colName)
    {
        FieldKey fKey = FieldKey.fromString(colName);
        String caption = fKey.getCaption();
        Map<FieldKey,ColumnInfo> columns = getAvailableColumns();
        ColumnInfo column = columns.get(fKey);
        if (column != null)
            caption = column.getLabel();
        return caption;
    }

    public List<String> getSortColumnNamesFromURL()
    {
        ActionURL url = getSourceURL();
        if (url == null)
            return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        for (String key : url.getKeysByPrefix(getDataRegionName() + "."))
        {
            String editedKey = key.substring(getDataRegionName().length() + 1);
            if ("sort".equals(editedKey))
            {
                String colName = url.getParameter(key);
                result.add(getCaption(colName));
            }
        }
        return result;
    }

    public String getContainerFilterName()
    {
        ActionURL url = getSourceURL();
        CustomView current = getCustomView();
        if (current != null && current.getContainerFilterName() != null)
        {
            if (url.getParameter(getDataRegionName() + "." + QueryParam.ignoreFilter.toString()) != null)
                return current.getContainerFilterName();
        }
        if (null == url)
            return null;
        return url.getParameter(getDataRegionName() + ".containerFilterName");
    }

    public boolean hasFilterOrSort()
    {
        CustomView current = getCustomView();
        ActionURL url = getSourceURL();
        if (null == url)
            return false;
        if (current != null && current.hasFilterOrSort())
        {
            if (url.getParameter(getDataRegionName() + "." + QueryParam.ignoreFilter.toString()) != null)
                return true; 
        }
        for (String key : url.getKeysByPrefix(getDataRegionName() + "."))
        {
            if (isFilterOrSort(getDataRegionName(), key))
                return true;
        }
        return false;
    }

    public void applyFilterAndSortToURL(ActionURL url, String dataRegionName)
    {
        ActionURL src = getSourceURL();
        if (null == src)
            return;
        if (src.getParameter(getDataRegionName() + "." + QueryParam.ignoreFilter.toString()) == null)
        {
            CustomView current = getCustomView();
            if (current != null)
            {
                current.applyFilterAndSortToURL(url, dataRegionName);
            }
        }
        for (String key : src.getKeysByPrefix(getDataRegionName() + "."))
        {
            if (!isFilterOrSort(getDataRegionName(), key))
                continue;
            String newKey = dataRegionName + key.substring(getDataRegionName().length());
            for (String value : src.getParameters(key))
            {
                url.addParameter(newKey, value);
            }
        }

        Sort sort = new Sort();
        sort.applyURLSort(url, dataRegionName);
        url.replaceParameter(dataRegionName+".sort",sort.getURLParamValue());
    }
    

    public ActionURL getSourceURL()
    {
        return _sourceURL;        
    }


    public void setFf_saveFilter(boolean b)
    {
        ff_saveFilter = b;
    }

    public boolean canSaveForAllUsers()
    {
        return getContainer().hasPermission(getUser(), EditSharedViewPermission.class);
    }

    public boolean canEdit()
    {
        return !isCustomViewInherited();
    }
}
