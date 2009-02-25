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

package org.labkey.query.persist;

import org.labkey.api.data.Entity;
import org.labkey.api.data.Container;
import org.labkey.api.data.CacheKey;

public class DbUserSchemaDef extends Entity
{
    public enum Column
    {
        container,
        userschemaname
    }
    static public class Key extends CacheKey<DbUserSchemaDef, Column>
    {
        public Key(Container container)
        {
            super(QueryManager.get().getTableInfoDbUserSchema(), DbUserSchemaDef.class, container);
        }
        public void setUserSchemaName(String name)
        {
            addCondition(Column.userschemaname, name);
        }
    }
    private int _dbUserSchemaId;
    private String _userSchemaName;
    private String _dbSchemaName;
    private String _dbContainer;
    private boolean _editable;
    private String _metadata;

    public int getDbUserSchemaId()
    {
        return _dbUserSchemaId;
    }

    public void setDbUserSchemaId(int id)
    {
        _dbUserSchemaId = id;
    }

    public String getUserSchemaName()
    {
        return _userSchemaName;
    }
    public void setUserSchemaName(String name)
    {
        _userSchemaName = name;
    }

    public String getDbSchemaName()
    {
        return _dbSchemaName;
    }

    public void setDbSchemaName(String name)
    {
        _dbSchemaName = name;
    }

    public String getDbContainer()
    {
        return _dbContainer;
    }
    public void setDbContainer(String id)
    {
        _dbContainer = id;
    }

    public String getMetaData()
    {
        return _metadata;
    }

    public void setMetaData(String metadata)
    {
        _metadata = metadata;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DbUserSchemaDef that = (DbUserSchemaDef) o;

        if (_dbUserSchemaId != that._dbUserSchemaId) return false;
        if (_dbContainer != null ? !_dbContainer.equals(that._dbContainer) : that._dbContainer != null) return false;
        if (_dbSchemaName != null ? !_dbSchemaName.equals(that._dbSchemaName) : that._dbSchemaName != null)
            return false;
        if (_metadata != null ? !_metadata.equals(that._metadata) : that._metadata != null) return false;
        if (_userSchemaName != null ? !_userSchemaName.equals(that._userSchemaName) : that._userSchemaName != null)
            return false;

        return true;
    }

    public int hashCode()
    {
        int result;
        result = _dbUserSchemaId;
        result = 31 * result + (_userSchemaName != null ? _userSchemaName.hashCode() : 0);
        result = 31 * result + (_dbSchemaName != null ? _dbSchemaName.hashCode() : 0);
        result = 31 * result + (_dbContainer != null ? _dbContainer.hashCode() : 0);
        result = 31 * result + (_metadata != null ? _metadata.hashCode() : 0);
        return result;
    }

    public boolean isEditable()
    {
        return _editable;
    }

    public void setEditable(boolean editable)
    {
        _editable = editable;
    }
}
