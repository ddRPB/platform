/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleQueryUpdateService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: 5/18/13
 */
public class ExtendedTableUpdateService extends SimpleQueryUpdateService
{
    private final AbstractQueryUpdateService _baseTableUpdateService;

    public ExtendedTableUpdateService(SimpleUserSchema.SimpleTable queryTable, TableInfo dbTable, AbstractQueryUpdateService baseQUS)
    {
        super(queryTable, dbTable);
        _baseTableUpdateService = baseQUS;
    }

    protected AbstractQueryUpdateService getBaseTableUpdateService()
    {
        return _baseTableUpdateService;
    }

    @Override
    protected Map<String, Object> _select(Container container, Object[] keys) throws SQLException, ConversionException
    {
        return super._select(container, keys);
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        return super.insertRows(user, container, rows, errors, extraScriptContext);
    }

    @Override
    protected Map<String, Object> _insert(User user, Container c, Map<String, Object> row) throws SQLException, ValidationException
    {
        return super._insert(user, c, row);
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        Map<String, Object> updatedRow = super.updateRow(user, container, row, oldRow);
        try
        {
            _baseTableUpdateService.updateRows(user, container, Arrays.asList(row), Arrays.asList(oldRow), null);
        }
        catch (BatchValidationException e)
        {
            throw new QueryUpdateServiceException(e);
        }
        return updatedRow;
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        // Delete this extended table record before deleting the parent table record.
        Map<String, Object> row = super.deleteRow(user, container, oldRowMap);
        try
        {
            _baseTableUpdateService.deleteRows(user, container, Arrays.asList(oldRowMap), null);
        }
        catch (BatchValidationException e)
        {
            throw new QueryUpdateServiceException(e);
        }
        return row;
    }


    @Override
    public DataIteratorBuilder createImportETL(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
    {
        return super.createImportETL(user, container, data, context);
    }

}
