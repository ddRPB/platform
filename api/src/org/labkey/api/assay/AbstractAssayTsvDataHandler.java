/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.assay;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.RemapCache;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableResultSet;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.validator.ColumnValidator;
import org.labkey.api.data.validator.ColumnValidators;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.AbstractExperimentDataHandler;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ProvenanceService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.qc.DataLoaderSettings;
import org.labkey.api.qc.ValidationDataHandler;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.ParticipantVisitResolver;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.springframework.jdbc.BadSqlGrammarException;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: jeckels
 * Date: Jan 3, 2008
 */
public abstract class AbstractAssayTsvDataHandler extends AbstractExperimentDataHandler implements ValidationDataHandler
{
    protected static final Object ERROR_VALUE = new Object() {
        @Override
        public String toString()
        {
            return "{AbstractAssayTsvDataHandler.ERROR_VALUE}";
        }
    };

    private static final Logger LOG = Logger.getLogger(AbstractAssayTsvDataHandler.class);

    protected abstract boolean allowEmptyData();

    public void importFile(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context) throws ExperimentException
    {
        ExpProtocolApplication sourceApplication = data.getSourceApplication();
        if (sourceApplication == null)
        {
            throw new ExperimentException("Cannot import a TSV without knowing its assay definition");
        }
        ExpRun run = sourceApplication.getRun();
        ExpProtocol protocol = run.getProtocol();
        AssayProvider provider = AssayService.get().getProvider(protocol);

        DataLoaderSettings settings = new DataLoaderSettings();

        Map<DataType, List<Map<String, Object>>> rawData = getValidationDataMap(data, dataFile, info, log, context, settings);
        assert(rawData.size() <= 1);
        try
        {
            importRows(data, info.getUser(), run, protocol, provider, rawData.values().iterator().next(), settings);
        }
        catch (ValidationException e)
        {
            throw new ExperimentException(e.toString(), e);
        }
    }

    public void importTransformDataMap(ExpData data, AssayRunUploadContext context, ExpRun run, List<Map<String, Object>> dataMap) throws ExperimentException
    {
        try
        {
            DataLoaderSettings settings = new DataLoaderSettings();
            importRows(data, context.getUser(), run, context.getProtocol(), context.getProvider(), dataMap, settings);
        }
        catch (ValidationException e)
        {
            throw new ExperimentException(e.toString(), e);
        }
    }

    @Override
    public Map<DataType, List<Map<String, Object>>> getValidationDataMap(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context, DataLoaderSettings settings) throws ExperimentException
    {
        ExpProtocol protocol = data.getRun().getProtocol();
        AssayProvider provider = AssayService.get().getProvider(protocol);

        Domain dataDomain = provider.getResultsDomain(protocol);

        try (DataLoader loader = createLoaderForImport(dataFile, dataDomain, settings, true))
        {
            Map<DataType, List<Map<String, Object>>> datas = new HashMap<>();
            List<Map<String, Object>> dataRows = loader.load();

            // loader did not parse any rows
            if (dataRows.isEmpty() && !settings.isAllowEmptyData() && dataDomain.getProperties().size() > 0)
                throw new ExperimentException("Unable to load any rows from the input data. Please check the format of the input data to make sure it matches the assay data columns.");
            if (!dataRows.isEmpty())
                adjustFirstRowOrder(dataRows, loader);

            datas.put(getDataType(), dataRows);
            return datas;
        }
        catch (IOException ioe)
        {
            throw new ExperimentException("There was a problem loading the data file. " + (ioe.getMessage() == null ? "" : ioe.getMessage()), ioe);
        }
    }

    /**
     * Creates a DataLoader that can handle missing value indicators if the columns on the domain
     * are configured to support it.
     *
     * @throws ExperimentException
     */
    public static DataLoader createLoaderForImport(File dataFile, @Nullable Domain dataDomain, DataLoaderSettings settings, boolean shouldInferTypes) throws ExperimentException
    {
        Map<String, DomainProperty> aliases = new HashMap<>();
        Set<String> mvEnabledColumns = Sets.newCaseInsensitiveHashSet();
        Set<String> mvIndicatorColumns = Sets.newCaseInsensitiveHashSet();

        if (dataDomain != null)
        {
            List<? extends DomainProperty> columns = dataDomain.getProperties();
            aliases = dataDomain.createImportMap(false);
            for (DomainProperty col : columns)
            {
                if (col.isMvEnabled())
                {
                    // Check for all of the possible names for the column in the incoming data when deciding if we should
                    // check it for missing values
                    Set<String> columnAliases = ImportAliasable.Helper.createImportMap(Collections.singletonList(col), false).keySet();
                    mvEnabledColumns.addAll(columnAliases);
                    mvIndicatorColumns.add(col.getName() + MvColumn.MV_INDICATOR_SUFFIX);
                }
            }
        }

        try
        {
            DataLoader loader = DataLoader.get().createLoader(dataFile, null, true, null, TabLoader.TSV_FILE_TYPE);
            loader.setThrowOnErrors(settings.isThrowOnErrors());
            loader.setInferTypes(shouldInferTypes);

            for (ColumnDescriptor column : loader.getColumns())
            {
                if (dataDomain != null)
                {
                    if (mvEnabledColumns.contains(column.name))
                    {
                        column.setMvEnabled(dataDomain.getContainer());
                    }
                    else if (mvIndicatorColumns.contains(column.name))
                    {
                        column.setMvIndicator(dataDomain.getContainer());
                        column.clazz = String.class;
                    }
                    DomainProperty prop = aliases.get(column.name);
                    if (prop != null)
                    {
                        // Allow String values through if the column is a lookup and the settings allow lookups by alternate key.
                        // The lookup table unique indices or display column value will be used to convert the column to the lookup value.
                        if (!(settings.isAllowLookupByAlternateKey() && column.clazz == String.class && prop.getLookup() != null))
                        {
                            // Otherwise, just use the expected PropertyDescriptor's column type
                            column.clazz = prop.getPropertyDescriptor().getPropertyType().getJavaType();
                        }
                    }
                    else
                    {
                        // It's not an expected column. Is it an MV indicator column?
                        if (!settings.isAllowUnexpectedColumns() && !mvIndicatorColumns.contains(column.name))
                        {
                            column.load = false;
                        }
                    }
                }

                if (settings.isBestEffortConversion())
                    column.errorValues = DataLoader.ERROR_VALUE_USE_ORIGINAL;
                else
                    column.errorValues = ERROR_VALUE;
            }
            return loader;

        }
        catch (IOException ioe)
        {
            throw new ExperimentException("There was a problem loading the data file. " + (ioe.getMessage() == null ? "" : ioe.getMessage()), ioe);
        }
    }

    /**
     * Reorders the first row of the list of rows to be in original column order. This is usually enough
     * to cause serializers for tsv formats to respect the original file column order. A bit of a hack but
     * the way row maps are generated make it difficult to preserve order at row map generation time.
     */
    private void adjustFirstRowOrder(List<Map<String, Object>> dataRows, DataLoader loader) throws IOException
    {
        Map<String, Object> firstRow = dataRows.remove(0);
        Map<String, Object> newRow = new LinkedHashMap<>();

        for (ColumnDescriptor column : loader.getColumns())
        {
            if (firstRow.containsKey(column.name))
                newRow.put(column.name, firstRow.get(column.name));
        }
        dataRows.add(0, newRow);
    }

    @Override
    public void beforeDeleteData(List<ExpData> data, User user) throws ExperimentException
    {
        for (ExpData d : data)
        {
            ExpProtocolApplication sourceApplication = d.getSourceApplication();
            if (sourceApplication != null)
            {
                ExpRun run = sourceApplication.getRun();
                if (run != null)
                {
                    ExpProtocol protocol = run.getProtocol();
                    AssayProvider provider = AssayService.get().getProvider(protocol);

                    SQLFragment assayResultLsidSql = null;

                    Domain domain;
                    if (provider != null)
                    {
                        domain = provider.getResultsDomain(protocol);

                        AssayTableMetadata tableMetadata = provider.getTableMetadata(protocol);
                        AssayProtocolSchema assayProtocolSchema = provider.createProtocolSchema(user, protocol.getContainer(), protocol, null);
                        TableInfo assayDataTable = assayProtocolSchema.createDataTable(ContainerFilter.EVERYTHING, false);
                        if (assayDataTable != null)
                        {
                            ColumnInfo lsidCol = assayDataTable.getColumn("LSID");
                            ColumnInfo dataIdCol = assayDataTable.getColumn("DataId");
                            if (lsidCol == null || dataIdCol == null)
                                throw new IllegalStateException("Assay results table expected to have dataId lookup column and LSID column");

                            // select the assay results LSID column for all rows referenced by the data
                            assayResultLsidSql = new SQLFragment("SELECT ").append(lsidCol.getValueSql("X"))
                                    .append(" FROM ").append(assayDataTable.getFromSQL("X"))
                                    .append(" WHERE ").append(dataIdCol.getValueSql("X")).append(" = ").append(d.getRowId());
                        }
                    }
                    else
                    {
                        // Be tolerant of the AssayProvider no longer being available. See if we have the default
                        // results/data domain for TSV-style assays
                        try
                        {
                            domain = AbstractAssayProvider.getDomainByPrefix(protocol, ExpProtocol.ASSAY_DOMAIN_DATA);
                        }
                        catch (IllegalStateException ignored)
                        {
                            domain = null;
                            // Be tolerant of not finding a domain anymore, if the provider has gone away
                        }

                        // TODO: create assayResultLsidSql when provider no longer exists
                    }

                    // delete the assay result row exp.objects
                    if (assayResultLsidSql != null)
                    {
                        if (LOG.isDebugEnabled())
                        {
                            SQLFragment t = new SQLFragment("SELECT o.*")
                                    .append(" FROM ").append(OntologyManager.getTinfoObject(), "o")
                                    .append(" WHERE Container = ?").add(run.getContainer())
                                    .append(" AND ObjectURI IN (")
                                    .append(assayResultLsidSql)
                                    .append(")");
                            SqlSelector ss = new SqlSelector(ExperimentService.get().getSchema(), t);
                            try (TableResultSet rs = ss.getResultSet())
                            {
                                ResultSetUtil.logData(rs, LOG);
                            }
                            catch (SQLException x)
                            {
                                throw new RuntimeSQLException(x);
                            }
                        }

                        OntologyManager.deleteOntologyObjects(ExperimentService.get().getSchema(), assayResultLsidSql, run.getContainer(), false);
                    }

                    if (domain != null && domain.getStorageTableName() != null)
                    {
                        SQLFragment deleteSQL = new SQLFragment("DELETE FROM ");
                        deleteSQL.append(domain.getDomainKind().getStorageSchemaName());
                        deleteSQL.append(".");
                        deleteSQL.append(domain.getStorageTableName());
                        deleteSQL.append(" WHERE DataId = ?");
                        deleteSQL.add(d.getRowId());

                        try
                        {
                            new SqlExecutor(DbSchema.get(domain.getDomainKind().getStorageSchemaName())).execute(deleteSQL);
                        }
                        catch (BadSqlGrammarException x)
                        {
                            // (18035) presumably this is an optimistic concurrency problem and the table is gone
                            // postgres returns 42P01 in this case... SQL Server?
                            if (SqlDialect.isObjectNotFoundException(x))
                            {
                                // CONSIDER: unfortunately we can't swallow this exception, because Postgres leaves
                                // the connection in an unusable state
                            }
                            throw x;
                        }
                    }
                }
            }
        }
    }

    public void importRows(ExpData data, User user, ExpRun run, ExpProtocol protocol, AssayProvider provider, List<Map<String, Object>> rawData)
            throws ExperimentException, ValidationException
    {
        importRows(data, user, run, protocol, provider, rawData, null);
    }

    public void importRows(ExpData data, User user, ExpRun run, ExpProtocol protocol, AssayProvider provider, List<Map<String, Object>> rawData, @Nullable DataLoaderSettings settings)
            throws ExperimentException, ValidationException
    {
        if (settings == null)
            settings = new DataLoaderSettings();

        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            Container container = data.getContainer();
            ParticipantVisitResolver resolver = createResolver(user, run, protocol, provider, container);

            Domain dataDomain = provider.getResultsDomain(protocol);

            if (rawData.size() == 0)
            {
                if (allowEmptyData() || dataDomain.getProperties().isEmpty())
                {
                    transaction.commit();
                    return;
                }
                else
                {
                    throw new ExperimentException("Data file contained zero data rows");
                }
            }

            final TableInfo dataTable = provider.createProtocolSchema(user, container, protocol, null).createDataTable(null);

            Map<ExpMaterial, String> inputMaterials = checkData(container, user, dataTable, dataDomain, rawData, settings, resolver);

            List<Map<String, Object>> fileData = convertPropertyNamesToURIs(rawData, dataDomain);

            // Insert the data into the assay's data table.
            // On insert, the raw data will have the provisioned table's rowId added to the list of maps
            List<Map<String, Object>> inserted = insertRowData(data, user, container, run, protocol, provider, dataDomain, fileData, dataTable);

            // Attach run's final protocol application with output LSIDs for Assay Result rows
            addAssayResultRowsProvenance(user, container, protocol, run, provider, inserted);

            if (shouldAddInputMaterials())
            {
                AbstractAssayProvider.addInputMaterials(run, user, inputMaterials);
            }

            transaction.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
    }


    private void addAssayResultRowsProvenance(
            User user, Container container,
            ExpProtocol protocol, ExpRun run,
            AssayProvider provider,
            List<Map<String, Object>> insertedData)
    {
        ProvenanceService pvs = ProvenanceService.get();
        if (pvs == null)
            return;

        // get the LSID for the newly inserted assay result data rows
        TableInfo dataTable = provider.createProtocolSchema(user, container, protocol, null).getTable("Data", null);
        ColumnInfo lsidCol = dataTable.getColumn("LSID");
        if (lsidCol == null)
            throw new IllegalStateException("Assay results table LSID column required to attach provenance");

        ColumnInfo rowIdCol = dataTable.getColumn("RowId");
        if (rowIdCol == null)
            throw new IllegalStateException("Assay results table RowId column required to attach provenance");

        List<Integer> rowIds = insertedData.stream().map(row -> (Integer)row.get("rowId")).collect(Collectors.toList());

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("run"), run.getRowId());
        filter.addCondition(rowIdCol, rowIds, CompareType.IN);
        Sort sort = new Sort(FieldKey.fromParts("rowId"));
        Map<Integer, String> rowIdToLsid = new TableSelector(dataTable, List.of(rowIdCol, lsidCol), filter, sort).getValueMap();
        assert rowIds.size() == rowIds.size();

        Set<Pair<String, String>> provPairs = new HashSet<>(insertedData.size());
        for (Map<String, Object> insertedRow : insertedData)
        {
            Integer rowId = (Integer)insertedRow.get("rowId");
            if (rowId == null)
                throw new IllegalStateException("Assay results rowId required to attach provenance: " + insertedRow);

            String rowOutputLsid = rowIdToLsid.get(rowId);
            if (rowOutputLsid == null)
                throw new IllegalStateException("Assay results LSID required to attach provenance: " + insertedRow);

            Set<String> rowInputLsids = (Set<String>)insertedRow.get(ProvenanceService.PROVENANCE_INPUT_PROPERTY);
            if (rowInputLsids != null && !rowInputLsids.isEmpty())
            {
                for (String rowInputLsid : rowInputLsids)
                {
                    provPairs.add(Pair.of(rowInputLsid, rowOutputLsid));
                }
            }
            else
            {
                provPairs.add(Pair.of(null, rowOutputLsid));
            }
        }

        ExpProtocolApplication outputProtocolApp = run.getOutputProtocolApplication();

        if (!provPairs.isEmpty())
        {
            pvs.addProvenance(container, outputProtocolApp, provPairs);
        }
    }

    protected ParticipantVisitResolver createResolver(User user, ExpRun run, ExpProtocol protocol, AssayProvider provider, Container container)
            throws IOException, ExperimentException
    {
        return AssayService.get().createResolver(user, run, protocol, provider, null);
    }

    /** Insert the data into the database.  Transaction is active. */
    protected List<Map<String, Object>> insertRowData(ExpData data, User user, Container container, ExpRun run, ExpProtocol protocol, AssayProvider provider, Domain dataDomain, List<Map<String, Object>> fileData, TableInfo tableInfo)
            throws SQLException, ValidationException
    {
        if (tableInfo instanceof UpdateableTableInfo)
        {
            return OntologyManager.insertTabDelimited(tableInfo, container, user, new SimpleAssayDataImportHelper(data), fileData, LOG);
        }
        else
        {
            Integer id = OntologyManager.ensureObject(container, data.getLSID());
            List<String> lsids = OntologyManager.insertTabDelimited(container, user, id,
                    new SimpleAssayDataImportHelper(data), dataDomain, fileData, false);
            // TODO: Add LSID values into return value rows
            return fileData;
        }
    }

    protected abstract boolean shouldAddInputMaterials();

    // NOTE: Calls filterColumns which mutates rawData in-place
    private void checkColumns(Domain dataDomain, Set<String> actual, List<String> missing, List<String> unexpected, List<Map<String, Object>> rawData, boolean strict)
    {
        Set<String> checkSet = new CaseInsensitiveHashSet();
        List<? extends DomainProperty> expected = dataDomain.getProperties();
        for (DomainProperty pd : expected)
        {
            checkSet.add(pd.getName());
            if (pd.isMvEnabled())
                checkSet.add((pd.getName() + MvColumn.MV_INDICATOR_SUFFIX));
        }
        for (String col : actual)
        {
            if (!checkSet.contains(col))
                unexpected.add(col);
        }
        if (!strict)
        {
            if (unexpected.size() > 0)
                filterColumns(dataDomain, actual, rawData);
            unexpected.clear();
        }

        // Now figure out what's missing but required
        Map<String, DomainProperty> importMap = dataDomain.createImportMap(true);
        // Consider all of them initially
        LinkedHashSet<DomainProperty> missingProps = new LinkedHashSet<>(expected);

        // Iterate through the ones we got
        for (String col : actual)
        {
            // Find the property that it maps to (via name, label, import alias, etc)
            DomainProperty prop = importMap.get(col);
            if (prop != null)
            {
                // If there's a match, don't consider it missing any more
                missingProps.remove(prop);
            }
        }

        for (DomainProperty pd : missingProps)
        {
            if ((pd.isRequired() || strict))
                missing.add(pd.getName());
        }
    }

    // NOTE: Mutates the rawData list in-place
    private void filterColumns(Domain domain, Set<String> actual, List<Map<String, Object>> rawData)
    {
        Map<String,String> expectedKey2ActualKey = new HashMap<>();
        for (Map.Entry<String,DomainProperty> aliased : domain.createImportMap(true).entrySet())
        {
            for (String actualKey : actual)
            {
                if (actualKey.equalsIgnoreCase(aliased.getKey()))
                {
                    expectedKey2ActualKey.put(aliased.getValue().getName(), actualKey);
                }

                if (actualKey.equalsIgnoreCase(ProvenanceService.PROVENANCE_INPUT_PROPERTY))
                {
                    expectedKey2ActualKey.put(actualKey, actualKey);
                }
            }
        }
        ListIterator<Map<String, Object>> iter = rawData.listIterator();
        while (iter.hasNext())
        {
            Map<String, Object> filteredMap = new HashMap<>();
            Map<String, Object> rawDataRow = iter.next();
            for (Map.Entry<String,String> expectedAndActualKeys : expectedKey2ActualKey.entrySet())
            {
                filteredMap.put(expectedAndActualKeys.getKey(), rawDataRow.get(expectedAndActualKeys.getValue()));
            }
            iter.set(filteredMap);
        }
    }

    /**
     * TODO: Replace with a DataIterator pipeline
     * NOTE: Mutates the rawData list in-place
     * @return the set of materials that are inputs to this run
     */
    private Map<ExpMaterial, String> checkData(Container container, User user, TableInfo dataTable, Domain dataDomain, List<Map<String, Object>> rawData, DataLoaderSettings settings, ParticipantVisitResolver resolver)
            throws ValidationException, ExperimentException
    {
        final ExperimentService exp = ExperimentService.get();
        final ProvenanceService pvs = ProvenanceService.get();

        List<String> missing = new ArrayList<>();
        List<String> unexpected = new ArrayList<>();

        Set<String> columnNames = Collections.emptySet();
        if (rawData != null && !rawData.isEmpty() && rawData.get(0) != null)
            columnNames = rawData.get(0).keySet();

        // For now, we'll only enforce that required columns are present.  In the future, we'd like to
        // do a strict check first, and then present ignorable warnings.
        checkColumns(dataDomain, columnNames, missing, unexpected, rawData, false);
        if (!missing.isEmpty() || !unexpected.isEmpty())
        {
            StringBuilder builder = new StringBuilder();
            if (!missing.isEmpty())
            {
                builder.append("Expected columns were not found: ");
                for (java.util.Iterator<String> it = missing.iterator(); it.hasNext();)
                {
                    builder.append(it.next());
                    if (it.hasNext())
                        builder.append(", ");
                    else
                        builder.append(".  ");
                }
            }
            if (!unexpected.isEmpty())
            {
                builder.append("Unexpected columns were found: ");
                for (java.util.Iterator<String> it = unexpected.iterator(); it.hasNext();)
                {
                    builder.append(it.next());
                    if (it.hasNext())
                        builder.append(", ");
                }
            }
            throw new ValidationException(builder.toString());
        }

        DomainProperty participantPD = null;
        DomainProperty specimenPD = null;
        DomainProperty visitPD = null;
        DomainProperty datePD = null;
        DomainProperty targetStudyPD = null;

        RemapCache cache = new RemapCache();
        Map<DomainProperty, TableInfo> remappableLookup = new HashMap<>();
        Map<Integer, ExpMaterial> materialCache = new HashMap<>();

        Map<DomainProperty, ExpSampleSet> lookupToSampleSetByName = new HashMap<>();
        Map<DomainProperty, ExpSampleSet> lookupToSampleSetById = new HashMap<>();
        Set<DomainProperty> lookupToAllSamplesByName = new HashSet<>();
        Set<DomainProperty> lookupToAllSamplesById = new HashSet<>();

        List<? extends DomainProperty> columns = dataDomain.getProperties();
        Map<DomainProperty, List<ColumnValidator>> validatorMap = new HashMap<>();

        for (DomainProperty pd : columns)
        {
            // initialize the DomainProperty validator map
            validatorMap.put(pd, ColumnValidators.create(null, pd));

            if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME) &&
                    pd.getPropertyDescriptor().getPropertyType() == PropertyType.STRING)
            {
                participantPD = pd;
            }
            else if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.SPECIMENID_PROPERTY_NAME) &&
                    pd.getPropertyDescriptor().getPropertyType() == PropertyType.STRING)
            {
                specimenPD = pd;
            }
            else if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.VISITID_PROPERTY_NAME) &&
                    pd.getPropertyDescriptor().getPropertyType() == PropertyType.DOUBLE)
            {
                visitPD = pd;
            }
            else if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.DATE_PROPERTY_NAME) &&
                    pd.getPropertyDescriptor().getPropertyType() == PropertyType.DATE_TIME)
            {
                datePD = pd;
            }
            else if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME) &&
                    pd.getPropertyDescriptor().getPropertyType() == PropertyType.STRING)
            {
                targetStudyPD = pd;
            }
            else
            {
                ExpSampleSet ss = DefaultAssayRunCreator.getLookupSampleSet(pd, container, user);
                if (ss != null)
                {
                    if (pd.getPropertyType().getJdbcType().isText())
                    {
                        lookupToSampleSetByName.put(pd, ss);
                    }
                    else
                    {
                        lookupToSampleSetById.put(pd, ss);
                    }
                }
                else if (DefaultAssayRunCreator.isLookupToMaterials(pd))
                {
                    if (pd.getPropertyType().getJdbcType().isText())
                        lookupToAllSamplesByName.add(pd);
                    else
                        lookupToAllSamplesById.add(pd);
                }
            }

            if (dataTable != null && settings.isAllowLookupByAlternateKey())
            {
                ColumnInfo column = dataTable.getColumn(pd.getName());
                ForeignKey fk = column != null ? column.getFk() : null;
                if (fk != null && fk.allowImportByAlternateKey())
                {
                    remappableLookup.put(pd, fk.getLookupTableInfo());
                }
            }
        }

        boolean resolveMaterials = specimenPD != null || visitPD != null || datePD != null || targetStudyPD != null;

        Set<String> wrongTypes = new HashSet<>();

        Map<ExpMaterial, String> materialInputs = new LinkedHashMap<>();

        Map<String, DomainProperty> aliasMap = dataDomain.createImportMap(true);

        // We want to share canonical casing between data rows, or we end up with an extra Map instance for each
        // data row which can add up quickly
        CaseInsensitiveHashMap<Object> caseMapping = new CaseInsensitiveHashMap<>();
        ValidatorContext validatorContext = new ValidatorContext(container, user);

        int rowNum = 0;
        for (ListIterator<Map<String, Object>> iter = rawData.listIterator(); iter.hasNext();)
        {
            rowNum++;
            Collection<ValidationError> errors = new ArrayList<>();
            Set<String> rowInputLSIDs = new HashSet<>();

            Map<String, Object> originalMap = iter.next();
            Map<String, Object> map = new CaseInsensitiveHashMap<>(caseMapping);
            // Rekey the map, resolving aliases to the actual property names
            for (Map.Entry<String, Object> entry : originalMap.entrySet())
            {
                DomainProperty prop = aliasMap.get(entry.getKey());
                if (prop != null)
                {
                    map.put(prop.getName(), entry.getValue());
                }
                else if (entry.getKey().equalsIgnoreCase(ProvenanceService.PROVENANCE_INPUT_PROPERTY))
                {
                    map.put(entry.getKey(), entry.getValue());
                }
            }

            String participantID = null;
            String specimenID = null;
            Double visitID = null;
            Date date = null;
            Container targetStudy = null;

            for (DomainProperty pd : columns)
            {
                Object o = map.get(pd.getName());
                if (o instanceof String)
                {
                    o = StringUtils.trimToNull((String) o);
                    map.put(pd.getName(), o);
                    iter.set(map);
                }

                // validate the data value
                if (validatorMap.containsKey(pd))
                {
                    for (ColumnValidator validator : validatorMap.get(pd))
                    {
                        String error = validator.validate(rowNum, o, validatorContext);
                        if (error != null)
                            errors.add(new PropertyValidationError(error, pd.getName()));
                    }
                }

                if (participantPD == pd)
                {
                    participantID = o instanceof String ? (String)o : null;
                }
                else if (specimenPD == pd)
                {
                    specimenID = o instanceof String ? (String)o : null;
                }
                else if (visitPD == pd && o != null)
                {
                    visitID = o instanceof Number ? ((Number)o).doubleValue() : null;
                }
                else if (datePD == pd && o != null)
                {
                    date = o instanceof Date ? (Date) o : null;
                }
                else if (targetStudyPD == pd && o != null)
                {
                    Set<Study> studies = StudyService.get().findStudy(o, null);
                    if (studies.isEmpty())
                    {
                        errors.add(new PropertyValidationError("Couldn't resolve " + pd.getName() + " '" + o.toString() + "' to a study folder.", pd.getName()));
                    }
                    else if (studies.size() > 1)
                    {
                        errors.add(new PropertyValidationError("Ambiguous " + pd.getName() + " '" + o.toString() + "'.", pd.getName()));
                    }
                    if (!studies.isEmpty())
                    {
                        Study study = studies.iterator().next();
                        targetStudy = study != null ? study.getContainer() : null;
                    }
                }

                boolean valueMissing;
                if (o == null)
                {
                    valueMissing = true;
                }
                else if (o instanceof MvFieldWrapper)
                {
                    MvFieldWrapper mvWrapper = (MvFieldWrapper)o;
                    if (mvWrapper.isEmpty())
                        valueMissing = true;
                    else
                    {
                        valueMissing = false;
                        if (!MvUtil.isValidMvIndicator(mvWrapper.getMvIndicator(), dataDomain.getContainer()))
                        {
                            String columnName = pd.getName() + MvColumn.MV_INDICATOR_SUFFIX;
                            wrongTypes.add(columnName);
                            errors.add(new PropertyValidationError(columnName + " must be a valid MV indicator.", columnName));
                        }
                    }

                }
                else
                {
                    valueMissing = false;
                }

                // If the column is a file link or attachment, resolve the value to a File object
                String uri = pd.getType().getTypeURI();
                if (uri.equals(PropertyType.FILE_LINK.getTypeUri()) || uri.equals(PropertyType.ATTACHMENT.getTypeUri()))
                {
                    if ("".equals(o))
                    {
                        // Issue 36502: If the original input was an empty value, set it to null so we won't store an empty string in the database
                        o = null;
                        map.put(pd.getName(), null);
                    }
                    else
                    {
                        // File column values are stored as the absolute resolved path
                        File resolvedFile = AssayUploadFileResolver.resolve(o, container, pd);
                        if (resolvedFile != null)
                        {
                            o = resolvedFile;
                            map.put(pd.getName(), o);
                            iter.set(map);
                        }
                    }
                }

                // If we have a String value for a lookup column, attempt to use the table's unique indices or display value to convert the String into the lookup value
                // See similar conversion performed in SimpleTranslator.RemapPostConvertColumn
                if (o instanceof String && remappableLookup.containsKey(pd))
                {
                    TableInfo lookupTable = remappableLookup.get(pd);
                    Object remapped = cache.remap(lookupTable, user, container, ContainerFilter.Type.CurrentPlusProjectAndShared, (String)o);
                    if (remapped == null)
                    {
                        errors.add(new PropertyValidationError("Failed to convert '" + pd.getName() + "': " + o, pd.getName()));
                    }
                    else if (o != remapped)
                    {
                        o = remapped;
                        map.put(pd.getName(), remapped);
                        iter.set(map);
                    }
                }

                if (!valueMissing && o == ERROR_VALUE && !wrongTypes.contains(pd.getName()))
                {
                    wrongTypes.add(pd.getName());
                    errors.add(new PropertyValidationError(pd.getName() + " must be of type " + ColumnInfo.getFriendlyTypeName(pd.getPropertyDescriptor().getPropertyType().getJavaType()) + ".", pd.getName()));
                }

                // Collect sample names or ids for each of the SampleSet lookup columns
                // Add any sample inputs to the rowInputLSIDs
                ExpSampleSet byNameSS = lookupToSampleSetByName.get(pd);
                if (o instanceof String && (byNameSS != null || lookupToAllSamplesByName.contains(pd)))
                {
                    String ssName = byNameSS != null ? byNameSS.getName() : null;
                    ExpMaterial material = exp.findExpMaterial(container, user, ssName, (String)o, cache, materialCache);
                    if (material != null)
                    {
                        materialInputs.putIfAbsent(material, pd.getName());
                        rowInputLSIDs.add(material.getLSID());
                    }
                }

                if (o instanceof Integer && (lookupToSampleSetById.containsKey(pd) || lookupToAllSamplesById.contains(pd)))
                {
                    ExpMaterial material = materialCache.computeIfAbsent((Integer)o, exp::getExpMaterial);
                    if (material != null)
                    {
                        materialInputs.putIfAbsent(material, pd.getName());
                        rowInputLSIDs.add(material.getLSID());
                    }
                }
            }

            if (!errors.isEmpty())
                throw new ValidationException(errors, rowNum);

            ParticipantVisit participantVisit = resolver.resolve(specimenID, participantID, visitID, date, targetStudy);
            if (participantPD != null && map.get(participantPD.getName()) == null)
            {
                map.put(participantPD.getName(), participantVisit.getParticipantID());
                iter.set(map);
            }
            if (visitPD != null && map.get(visitPD.getName()) == null)
            {
                map.put(visitPD.getName(), participantVisit.getVisitID());
                iter.set(map);
            }
            if (datePD != null && map.get(datePD.getName()) == null)
            {
                map.put(datePD.getName(), participantVisit.getDate());
                iter.set(map);
            }
            if (targetStudyPD != null && participantVisit.getStudyContainer() != null)
            {
                // Original TargetStudy value may have been a container id, container path, or a study label.
                // Store all TargetStudy values as Container ID string.
                map.put(targetStudyPD.getName(), participantVisit.getStudyContainer().getId());
                iter.set(map);
            }

            if (resolveMaterials)
            {
                ExpMaterial material = participantVisit.getMaterial();
                if (material != null)
                {
                    materialInputs.putIfAbsent(material, null);
                    rowInputLSIDs.add(material.getLSID());
                }
            }

            // Add any “prov:objectInputs” to the rowInputLSIDs
            var provenanceInputs = map.get(ProvenanceService.PROVENANCE_INPUT_PROPERTY);
            if (null != provenanceInputs)
            {
                if (pvs == null)
                    throw new ExperimentException("Provenance service not available");

                if (provenanceInputs instanceof JSONArray)
                {
                    JSONArray inputJSONArr = (JSONArray) provenanceInputs;
                    Object[] inputArr = inputJSONArr.toArray();
                    for (Object lsid: inputArr)
                    {
                        rowInputLSIDs.add(lsid.toString());
                    }
                }
                else
                {
                    String lsids = (String) map.get(ProvenanceService.PROVENANCE_INPUT_PROPERTY);
                    String[] lsidArr = lsids.split(",");
                    rowInputLSIDs.addAll(Arrays.asList(lsidArr));
                }
            }

            if (!rowInputLSIDs.isEmpty())
            {
                map.put(ProvenanceService.PROVENANCE_INPUT_PROPERTY, rowInputLSIDs);
                iter.set(map);
            }
        }

        return materialInputs;
    }


    /** Wraps each map in a version that can be queried based on on any of the aliases (name, property URI, import
     * aliases, etc for a given property */
    // NOTE: Mutates the rawData list in place
    protected List<Map<String, Object>> convertPropertyNamesToURIs(List<Map<String, Object>> dataMaps, Domain domain)
    {
        // Get the mapping of different names to the set of domain properties
        final Map<String, DomainProperty> importMap = domain.createImportMap(true);

        // For a given property, find all the potential names it by which it could be referenced
        final Map<DomainProperty, Set<String>> propToNames = new HashMap<>();
        for (Map.Entry<String, DomainProperty> entry : importMap.entrySet())
        {
            Set<String> allNames = propToNames.get(entry.getValue());
            if (allNames == null)
            {
                allNames = new HashSet<>();
                propToNames.put(entry.getValue(), allNames);
            }
            allNames.add(entry.getKey());
        }
        
        // We want to share canonical casing between data rows, or we end up with an extra Map instance for each
        // data row which can add up quickly
        CaseInsensitiveHashMap<Object> caseMapping = new CaseInsensitiveHashMap<>();
        for (ListIterator<Map<String, Object>> i = dataMaps.listIterator(); i.hasNext(); )
        {
            Map<String, Object> dataMap = i.next();
            CaseInsensitiveHashMap<Object> newMap = new PropertyLookupMap(dataMap, caseMapping, importMap, propToNames);

            // Swap out the entry in the list with the transformed map
            i.set(newMap);
        }
        return dataMaps;
    }

    public void deleteData(ExpData data, Container container, User user)
    {
        OntologyManager.deleteOntologyObjects(container, data.getLSID());
    }

    public ActionURL getContentURL(ExpData data)
    {
        ExpRun run = data.getRun();
        if (run != null)
        {
            ExpProtocol protocol = run.getProtocol();
            return PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(data.getContainer(), protocol, run.getRowId());
        }
        return null;
    }

    /** Wrapper around a row's key->value map that can find the values based on any of the DomainProperty's potential
     * aliases, like the property name, URI, import aliases, etc */
    private static class PropertyLookupMap extends CaseInsensitiveHashMap<Object>
    {
        private final Map<String, DomainProperty> _importMap;
        private final Map<DomainProperty, Set<String>> _propToNames;

        public PropertyLookupMap(Map<String, Object> dataMap, CaseInsensitiveHashMap<Object> caseMapping, Map<String, DomainProperty> importMap, Map<DomainProperty, Set<String>> propToNames)
        {
            super(dataMap, caseMapping);
            _importMap = importMap;
            _propToNames = propToNames;
        }

        @Override
        public Object get(Object key)
        {
            Object result = super.get(key);

            // If we can't find the value based on the name that was passed in, try any of its alternatives
            if (result == null && key instanceof String)
            {
                // Find the property that's associated with that name
                DomainProperty property = _importMap.get(key);
                if (property != null)
                {
                    // Find all of the potential synonyms
                    Set<String> allNames = _propToNames.get(property);
                    if (allNames != null)
                    {
                        for (String name : allNames)
                        {
                            // Look for a value under that name
                            result = super.get(name);
                            if (result != null)
                            {
                                break;
                            }
                        }
                    }
                }
            }
            return result;
        }
    }
}
