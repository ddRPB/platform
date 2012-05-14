/*
 * Copyright (c) 2004-2012 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.TableInsertDataIterator;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.AggregateRowConfig;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.MinorConfigurationException;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.SimpleNamedObject;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.ImportTemplateType;
import org.labkey.data.xml.TableType;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;


public class SchemaTableInfo implements TableInfo, UpdateableTableInfo
{
    private static final Logger _log = Logger.getLogger(SchemaTableInfo.class);

    // Table properties
    private final DbSchema _parentSchema;
    private final SQLFragment _selectName;
    private final String _metaDataName;
    private final DatabaseTableType _tableType;
    private final Path _notificationKey;

    // TODO: Remove -- temp hack for StorageProvisioner, which sets "study" as schema but loads meta data from "studydatasets" schema
    private String _metaDataSchemaName;

    private String _name;
    private String _description;
    private String _title = null;
    private int _cacheSize = DbCache.DEFAULT_CACHE_SIZE;
    private DetailsURL _gridURL;
    private DetailsURL _insertURL;
    private DetailsURL _importURL;
    private DetailsURL _deleteURL;
    private DetailsURL _updateURL;
    private DetailsURL _detailsURL;
    private ButtonBarConfig _buttonBarConfig;
    protected AggregateRowConfig _aggregateRowConfig;
    private boolean _hidden;
    protected String _importMsg;
    protected List<Pair<String, DetailsURL>> _importTemplates;

    // Column-related
    private TableType _xmlTable = null;
    private SchemaColumnMetaData _columnMetaData = null;
    protected boolean _autoLoadMetaData = true;
    private final Object _columnLock = new Object();
    private String _versionColumnName = null;
    private List<FieldKey> _defaultVisibleColumns = null;


    public SchemaTableInfo(DbSchema parentSchema, DatabaseTableType tableType, String tableName, String metaDataName, String selectName)
    {
        _parentSchema = parentSchema;
        _metaDataSchemaName = parentSchema.getName();
        _name = tableName;
        _metaDataName = metaDataName;
        _selectName = new SQLFragment(selectName);
        _tableType = tableType;
        _notificationKey = new Path(parentSchema.getClass().getName(), parentSchema.getName(), getClass().getName(), getName());
    }


    public SchemaTableInfo(DbSchema parentSchema, DatabaseTableType tableType, String tableMetaDataName)
    {
        this(parentSchema, tableType, tableMetaDataName, tableMetaDataName, parentSchema.getSqlDialect().makeLegalIdentifier(parentSchema.getName()) + "." + parentSchema.getSqlDialect().getSelectNameFromMetaDataName(tableMetaDataName));
    }


    TableType getXmlTable()
    {
        return _xmlTable;
    }

    public String getName()
    {
        return _name;
    }

    public String getMetaDataName()
    {
        return _metaDataName;
    }


    public String getSelectName()
    {
        return _selectName.getSQL();
    }


    @NotNull
    public SQLFragment getFromSQL()
    {
        return new SQLFragment().append("SELECT * FROM ").append(_selectName);
    }


    @NotNull
    public SQLFragment getFromSQL(String alias)
    {
        if (null != getSelectName())
            return new SQLFragment().append(getSelectName()).append(" ").append(alias);
        else
            return new SQLFragment().append("(").append(getFromSQL()).append(") ").append(alias);
    }


    public DbSchema getSchema()
    {
        return _parentSchema;
    }

    public AggregateRowConfig getAggregateRowConfig()
    {
        return _aggregateRowConfig;
    }

    public void setAggregateRowConfig(AggregateRowConfig config)
    {
        checkLocked();
        _aggregateRowConfig = config;
    }

    /** getSchema().getSqlDialect() */
    public SqlDialect getSqlDialect()
    {
        return _parentSchema.getSqlDialect();
    }


    @Override
    public List<String> getPkColumnNames()
    {
        return getColumnMetaData().getPkColumnNames();
    }

    @Override @NotNull
    public List<ColumnInfo> getPkColumns()
    {
        return getColumnMetaData().getPkColumns();
    }


    @Override
    public ColumnInfo getVersionColumn()
    {
        String versionColumnName = getVersionColumnName();

        return null == versionColumnName ? null : getColumn(versionColumnName);
    }


    @Override
    public String getVersionColumnName()
    {
        if (null == _versionColumnName)
        {
            if (null != getColumn("_ts"))
                _versionColumnName = "_ts";
            else if (null != getColumn("Modified"))
                _versionColumnName = "Modified";
        }

        return _versionColumnName;
    }

    @Override
    public boolean hasDefaultTitleColumn()
    {
        return getColumnMetaData().hasDefaultTitleColumn();
    }

    @Override
    public String getTitleColumn()
    {
        return getColumnMetaData().getTitleColumn();
    }

    @Override
    public DatabaseTableType getTableType()
    {
        return _tableType;
    }

    public int getCacheSize()
    {
        return _cacheSize;
    }

    public String toString()
    {
        return _selectName.toString();
    }


    public NamedObjectList getSelectList(String columnName)
    {
        if (columnName == null)
            return getSelectList();

        ColumnInfo column = getColumn(columnName);
        if (column == null /*|| column.isKeyField()*/)
            return new NamedObjectList();

        return getSelectList(Collections.<String>singletonList(column.getName()));
    }


    public NamedObjectList getSelectList()
    {
        return getSelectList(getPkColumnNames());
    }


    private NamedObjectList getSelectList(List<String> columnNames)
    {
        StringBuilder pkColumnSelect = new StringBuilder();
        String sep = "";

        for (String columnName : columnNames)
        {
            pkColumnSelect.append(sep);
            pkColumnSelect.append(columnName);
            sep = "+','+";
        }

        String cacheKey = "selectArray:" + pkColumnSelect;
        NamedObjectList list = (NamedObjectList) DbCache.get(this, cacheKey);

        if (null != list)
            return list;

        String titleColumn = getTitleColumn();

        ResultSet rs = null;
        list = new NamedObjectList();
        String sql = null;

        try
        {
            sql = "SELECT " + pkColumnSelect + " AS VALUE, " + titleColumn + " AS TITLE FROM " + _selectName.getSQL() + " ORDER BY " + titleColumn;

            rs = Table.executeQuery(_parentSchema, sql, null);

            while (rs.next())
            {
                list.put(new SimpleNamedObject(rs.getString(1), rs.getString(2)));
            }
        }
        catch (SQLException e)
        {
            _log.error(this + "\n" + sql, e);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }

        DbCache.put(this, cacheKey, list, getSelectListTimeout());
        return list;
    }


    protected long _selectListTimeout = TimeUnit.MINUTES.toMillis(1);

    protected long getSelectListTimeout()
    {
        return _selectListTimeout;
    }


    @Override
    public ColumnInfo getColumn(String colName)
    {
        return getColumnMetaData().getColumn(colName);
    }


    @Override
    public ColumnInfo getColumn(FieldKey name)
    {
        if (null != name.getParent())
            return null;
        return getColumn(name.getName());
    }


    @Deprecated
    public String getMetaDataSchemaName()
    {
        return _metaDataSchemaName;

    }

    @Deprecated
    public void setMetaDataSchemaName(String metaDataSchemaName)
    {
        _metaDataSchemaName = metaDataSchemaName;
    }


    protected SchemaColumnMetaData getColumnMetaData()
    {
        synchronized (_columnLock)
        {
            if (null == _columnMetaData)
            {
                try
                {
                    _columnMetaData = new SchemaColumnMetaData(this, _autoLoadMetaData);
                }
                catch (SQLException e)
                {
                    // See #14374
                    if ("com.mysql.jdbc.exceptions.jdbc4.MySQLSyntaxErrorException".equals(e.getClass().getName()) && e.getMessage().startsWith("SELECT command denied to user"))
                        throw new MinorConfigurationException("The LabKey database user doesn't have permissions to access this table", e);

                    throw new RuntimeSQLException(e);
                }
            }

            return _columnMetaData;
        }
    }


    public List<ColumnInfo> getColumns()
    {
        return Collections.unmodifiableList(getColumnMetaData().getColumns());
    }


    public List<ColumnInfo> getUserEditableColumns()
    {
        return getColumnMetaData().getUserEditableColumns();
    }


    public List<ColumnInfo> getColumns(String colNames)
    {
        String[] colNameArray = colNames.split(",");
        return getColumns(colNameArray);
    }

    public List<ColumnInfo> getColumns(String... colNameArray)
    {
        List<ColumnInfo> ret = new ArrayList<ColumnInfo>(colNameArray.length);

        for (String name : colNameArray)
        {
            ret.add(getColumn(name.trim()));
        }

        return Collections.unmodifiableList(ret);
    }


    public Set<String> getColumnNameSet()
    {
        return getColumnMetaData().getColumnNameSet();
    }


    void copyToXml(TableType xmlTable, boolean bFull)
    {
        xmlTable.setTableName(_name);
        xmlTable.setTableDbType(_tableType.name());

        if (bFull)
        {
            // changed to write out the value of property directly, without the
            // default calculation applied by the getter
            if (null != _title)
                xmlTable.setTableTitle(_title);
            if (_hidden)
                xmlTable.setHidden(true);
            if (null != _versionColumnName)
                xmlTable.setVersionColumnName(_versionColumnName);
        }

        getColumnMetaData().copyToXml(xmlTable, bFull);
    }


    void loadTablePropertiesFromXml(TableType xmlTable)
    {
        checkLocked();
        //Override with the table name from the schema so casing is nice...
        _name = xmlTable.getTableName();
        _description = xmlTable.getDescription();
        _hidden = xmlTable.getHidden();
        _title = xmlTable.getTableTitle();
        if (xmlTable.isSetCacheSize())
            _cacheSize = xmlTable.getCacheSize();

        if(xmlTable.getImportMessage() != null)
            setImportMessage(xmlTable.getImportMessage());

        if(xmlTable.getImportTemplates() != null)
            setImportTemplates(xmlTable.getImportTemplates().getTemplateArray());

        if (xmlTable.getGridUrl() != null)
        {
            _gridURL = DetailsURL.fromString(xmlTable.getGridUrl());
        }
        if (xmlTable.isSetImportUrl())
        {
            if (StringUtils.isBlank(xmlTable.getImportUrl()))
                _importURL = LINK_DISABLER;
            else
                _importURL = DetailsURL.fromString(xmlTable.getImportUrl());
        }
        if (xmlTable.isSetInsertUrl())
        {
            if (StringUtils.isBlank(xmlTable.getInsertUrl()))
                _insertURL = LINK_DISABLER;
            else
                _insertURL = DetailsURL.fromString(xmlTable.getInsertUrl());
        }
        if (xmlTable.isSetDeleteUrl())
        {
            if (StringUtils.isBlank(xmlTable.getDeleteUrl()))
                _deleteURL = LINK_DISABLER;
            else
                _deleteURL = DetailsURL.fromString(xmlTable.getDeleteUrl());
        }
        if (xmlTable.isSetUpdateUrl())
        {
            if (StringUtils.isBlank(xmlTable.getUpdateUrl()))
                _updateURL = LINK_DISABLER;
            else
                _updateURL = DetailsURL.fromString(xmlTable.getUpdateUrl());
        }
        if (xmlTable.getTableUrl() != null)
        {
            _detailsURL = DetailsURL.fromString(xmlTable.getTableUrl());
        }

        if (xmlTable.getButtonBarOptions() != null)
            _buttonBarConfig = new ButtonBarConfig(xmlTable.getButtonBarOptions());

        // Stash so we can overlay ColumnInfo properties later
        _xmlTable = xmlTable;
    }


    public ActionURL getGridURL(Container container)
    {
        if (_gridURL != null)
            return _gridURL.copy(container).getActionURL();
        return null;
    }

    public ActionURL getInsertURL(Container container)
    {
        if (_insertURL == null)
            return null;
        if (LINK_DISABLER == _insertURL)
            return LINK_DISABLER_ACTION_URL;
        return _insertURL.copy(container).getActionURL();
    }

    @Override
    public ActionURL getImportDataURL(Container container)
    {
        if (null == _importURL)
            return null;
        if (LINK_DISABLER == _importURL)
            return LINK_DISABLER_ACTION_URL;
        return _importURL.copy(container).getActionURL();
    }

    public ActionURL getDeleteURL(Container container)
    {
        if (_deleteURL == null)
            return null;
        if (LINK_DISABLER == _deleteURL)
            return LINK_DISABLER_ACTION_URL;
        return _deleteURL.copy(container).getActionURL();
    }

    public StringExpression getUpdateURL(Set<FieldKey> columns, Container container)
    {
        if (_updateURL == null)
            return null;
        if (LINK_DISABLER == _updateURL)
            return LINK_DISABLER;
        if (_updateURL.validateFieldKeys(columns))
            return _updateURL.copy(container);
        return null;
    }

    public StringExpression getDetailsURL(Set<FieldKey> columns, Container container)
    {
        ContainerContext containerContext = container;

        if (_detailsURL != null && _detailsURL.validateFieldKeys(columns))
        {
            return _detailsURL.copy(containerContext);
        }
        return null;
    }

    @Override
    public boolean hasDetailsURL()
    {
        return _detailsURL != null;
    }

    public Set<FieldKey> getDetailsURLKeys()
    {
        HashSet<FieldKey> set = new HashSet<FieldKey>();
        if (null != _detailsURL)
            set.addAll(_detailsURL.getFieldKeys());
        return set;
    }


    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return false;
    }

    public MethodInfo getMethod(String name)
    {
        return null;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        if (_defaultVisibleColumns != null)
            return _defaultVisibleColumns;
        return Collections.unmodifiableList(QueryService.get().getDefaultVisibleColumns(getColumns()));
    }

    public void setDefaultVisibleColumns(Iterable<FieldKey> keys)
    {
        checkLocked();
        _defaultVisibleColumns = new ArrayList<FieldKey>();
        for (FieldKey key : keys)
            _defaultVisibleColumns.add(key);
    }

    /** Used by SimpleUserSchema and external schemas to hide tables from the list of visible tables.  Not the same as isPublic(). */
    public boolean isHidden()
    {
        return _hidden;
    }

    public boolean isPublic()
    {
        //schema table infos are not public (i.e., not accessible from Query)
        return false;
    }

    public String getPublicName()
    {
        return null;
    }

    public String getPublicSchemaName()
    {
        return null;
    }

    public boolean needsContainerClauseAdded()
    {
        return true;
    }

    public ContainerFilter getContainerFilter()
    {
        return null;
    }

    public boolean isMetadataOverrideable()
    {
        return false;
    }

    @Override
    public void overlayMetadata(String tableName, UserSchema schema, Collection<QueryException> errors)
    {
        checkLocked();
        // no-op, we don't support metadata overrides
    }

    @Override
    public void overlayMetadata(TableType metadata, UserSchema schema, Collection<QueryException> errors)
    {
        checkLocked();
        // no-op, we don't support metadata overrides
    }

    public ButtonBarConfig getButtonBarConfig()
    {
        return _buttonBarConfig;
    }

    public ColumnInfo getLookupColumn(ColumnInfo parent, String name)
    {
        ForeignKey fk = parent.getFk();
        if (fk == null)
            return null;
        return fk.createLookupColumn(parent, name);
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        checkLocked();
        _description = description;
    }

    @Nullable
    public Domain getDomain()
    {
        return null;
    }

    @Nullable
    public DomainKind getDomainKind()
    {
        return null;
    }

    @Nullable
    public QueryUpdateService getUpdateService()
    {
        return null;
    }

    @Override @NotNull
    public Collection<QueryService.ParameterDecl> getNamedParameters()
    {
        return Collections.emptyList();
    }

    @Override
    public void fireBatchTrigger(Container c, TriggerType type, boolean before, BatchValidationException errors, Map<String, Object> extraContext) throws BatchValidationException
    {
        throw new UnsupportedOperationException("Table triggers not yet supported on schema tables");
    }

    @Override
    public void fireRowTrigger(Container c, TriggerType type, boolean before, int rowNumber, Map<String, Object> newRow, Map<String, Object> oldRow, Map<String, Object> extraContext) throws ValidationException
    {
        throw new UnsupportedOperationException("Table triggers not yet supported on schema tables");
    }

    @Override
    public boolean hasTriggers(Container c)
    {
        return false;
    }

    //
    // UpdateableTableInfo
    //

    @Override
    public boolean insertSupported()
    {
        return true;
    }

    @Override
    public boolean updateSupported()
    {
        return true;
    }

    @Override
    public boolean deleteSupported()
    {
        return true;
    }

    @Override
    public TableInfo getSchemaTableInfo()
    {
        return this;
    }

    @Override
    public ObjectUriType getObjectUriType()
    {
        return ObjectUriType.schemaColumn;
    }

    @Override
    public String getObjectURIColumnName()
    {
        return null;
    }

    @Override
    public String getObjectIdColumnName()
    {
        return null;
    }

    @Override
    public CaseInsensitiveHashMap<String> remapSchemaColumns()
    {
        return null;
    }

    @Override
    public CaseInsensitiveHashSet skipProperties()
    {
        return null;
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, BatchValidationException errors)
    {
        return TableInsertDataIterator.create(data, this, errors);
    }

    @Override
    public Parameter.ParameterMap insertStatement(Connection conn, User user) throws SQLException
    {
        return StatementUtils.insertStatement(conn, this, null, user, false, true);
    }

    @Override
    public Parameter.ParameterMap updateStatement(Connection conn, User user, Set<String> columns) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Parameter.ParameterMap deleteStatement(Connection conn) throws SQLException
    {
        return Table.deleteStatement(conn, this);
    }

    // TODO: Eliminate these setters/modifiers, making SchemaTableInfo effectively immutable... which would improve
    // concurrency, caching, performance, and maintainability

    protected void addColumn(ColumnInfo c)
    {
        checkLocked();
        getColumnMetaData().addColumn(c);
    }

    // Move an existing column to a different spot in the ordered list
    public void setColumnIndex(ColumnInfo c, int i)
    {
        checkLocked();
        getColumnMetaData().setColumnIndex(c, i);
    }

    public void setPkColumnNames(@NotNull List<String> pkColumnNames)
    {
        checkLocked();
        getColumnMetaData().setPkColumnNames(pkColumnNames);
    }

    @Override
    public Path getNotificationKey()
    {
        return _notificationKey;
    }

    @Override
    public String getDefaultDateFormat()
    {
        return null;
    }

    @Override
    public String getDefaultNumberFormat()
    {
        return null;
    }


    private void checkLocked()
    {
        if (_locked)
            throw new IllegalStateException("TableInfo is locked: " + getName());
    }

    boolean _locked;

    @Override
    public void setLocked(boolean b)
    {
        if (_locked && !b)
            throw new IllegalStateException("Can't unlock table: " + getName());
        List<ColumnInfo> cols = getColumns(); // may have side-effects
        _locked = b;
        for (ColumnInfo c : cols)
            c.setLocked(b);
    }

    @Override
    public boolean isLocked()
    {
        return _locked;
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return this instanceof ContainerFilterable;
    }

    @Override
    public String getImportMessage()
    {
        return _importMsg;
    }

    public void setImportMessage(String msg)
    {
        checkLocked();
        _importMsg = msg;
    }

    @Override
    public List<Pair<String, ActionURL>> getImportTemplates(Container c)
    {
        List<Pair<String, ActionURL>> templates = new ArrayList<Pair<String, ActionURL>>();
        if(_importTemplates != null)
        {
            for (Pair<String, DetailsURL> pair : _importTemplates)
            {
                templates.add(Pair.of(pair.first, pair.second.copy(c).getActionURL()));
            }
        }

        if (templates.size() == 0)
        {
            ActionURL url = PageFlowUtil.urlProvider(QueryUrls.class).urlCreateExcelTemplate(c, getPublicSchemaName(), getName());
            url.addParameter("captionType", ExcelWriter.CaptionType.Name.name());
            if(url != null)
                templates.add(Pair.of("Download Template", url));
        }

        return templates;
    }

    public void setImportTemplates(ImportTemplateType[] templates)
    {
        checkLocked();
        List<Pair<String, DetailsURL>> list = new ArrayList<Pair<String, DetailsURL>>();
        for (ImportTemplateType t : templates)
        {
            list.add(Pair.of(t.getLabel(), DetailsURL.fromString(t.getUrl()).fromString(t.getUrl())));
        }


        _importTemplates = list;
    }

    @Override
    public FieldKey getContainerFieldKey()
    {
        ColumnInfo col = getColumn("container");
        return null==col ? null : col.getFieldKey();
    }
}
