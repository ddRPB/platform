/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.data;

/**
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Jan 29, 2008
 * Time: 5:35:17 PM
 */
public class DimensionColumnInfo extends ColumnInfo
{
    private CrosstabTableInfo _table = null;
    private CrosstabDimension _dimension = null;

    public DimensionColumnInfo(CrosstabTableInfo table, CrosstabDimension dimension)
    {
        super(dimension.getSourceColumn(), table);
        _table = table;
        _dimension = dimension;
        setName(_dimension.getSourceColumn().getAlias());
        setCaption(_dimension.getSourceColumn().getCaption());
        setURL(dimension.getUrl());
    }

    public CrosstabDimension getDimension()
    {
        return _dimension;
    }

    public SQLFragment getValueSql(String tableAliasName)
    {
        return new SQLFragment(tableAliasName + "." + _dimension.getSourceColumn().getAlias());
    }
}
