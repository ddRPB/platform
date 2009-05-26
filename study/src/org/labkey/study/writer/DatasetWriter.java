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
package org.labkey.study.writer;

import org.labkey.api.data.*;
import org.labkey.api.util.VirtualFile;
import org.labkey.api.util.XmlBeanUtil;
import org.labkey.api.util.DateUtil;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Cohort;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.DatasetsDocument;
import org.labkey.study.xml.StudyDocument.Study.Datasets;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.ColumnType;
import org.apache.log4j.Logger;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 3:10:37 PM
 */
public class DatasetWriter implements Writer<Study>
{
    private static final Logger LOG = Logger.getLogger(DatasetWriter.class);
    private static final String DEFAULT_DIRECTORY = "datasets";
    private static final String MANIFEST_FILENAME = "datasets_manifest.xml";
    private static final String META_DATA_FILENAME = "datasets_meta_data.xml";

    public String getSelectionText()
    {
        return "Datasets";
    }

    public void write(Study study, ExportContext ctx, VirtualFile root) throws SQLException, IOException, ServletException
    {
        StudyDocument.Study studyXml = ctx.getStudyXml();
        Datasets datasetsXml = studyXml.addNewDatasets();
        datasetsXml.setDir(DEFAULT_DIRECTORY);
        datasetsXml.setFile(MANIFEST_FILENAME);

        VirtualFile fs = root.getDir(DEFAULT_DIRECTORY);
        DataSetDefinition[] datasets = study.getDataSets();

        DatasetsDocument manifestXml = DatasetsDocument.Factory.newInstance();
        DatasetsDocument.Datasets dsXml = manifestXml.addNewDatasets();
        String defaultDateFormat = StudyManager.getInstance().getDefaultDateFormatString(ctx.getContainer());
        String defaultNumberFormat = StudyManager.getInstance().getDefaultNumberFormatString(ctx.getContainer());
        if (null != defaultDateFormat)
            dsXml.setDefaultDateFormat(defaultDateFormat);
        if (null != defaultNumberFormat)
            dsXml.setDefaultNumberFormat(defaultNumberFormat);

        // Create <categories> element now so it appears first in the file
        DatasetsDocument.Datasets.Categories categoriesXml = dsXml.addNewCategories();
        DatasetsDocument.Datasets.Datasets2 datasets2Xml = dsXml.addNewDatasets();

        Set<String> categories = new LinkedHashSet<String>();

        for (DataSetDefinition def : datasets)
        {
            DatasetsDocument.Datasets.Datasets2.Dataset datasetXml = datasets2Xml.addNewDataset();
            datasetXml.setId(def.getDataSetId());

            Cohort cohort = def.getCohort();

            if (null != cohort)
                datasetXml.setCohort(cohort.getLabel());

            // Default value is "true"
            if (!def.isShowByDefault())
                datasetXml.setShowByDefault(def.isShowByDefault());

            String category = def.getCategory();

            if (null != category)
            {
                categories.add(category);
                datasetXml.setCategory(category);
            }
        }

        if (categories.isEmpty())
            dsXml.unsetCategories();     // Didn't need <categories> element after all
        else
            categoriesXml.setCategoryArray(categories.toArray(new String[categories.size()]));

        // Write out the schema.tsv file and add reference & attributes to study.xml
        SchemaWriter schemaWriter = new SchemaWriter();
        schemaWriter.write(datasets, ctx, fs);

        // Write out the .dataset file and add reference to study.xml
        Datasets.Definition definitionXml = datasetsXml.addNewDefinition();
        String datasetFilename = fs.makeLegalName(study.getLabel().replaceAll("\\s", "") + ".dataset");
        definitionXml.setFile(datasetFilename);

        PrintWriter writer = fs.getPrintWriter(datasetFilename);
        writer.println("# default group can be used to avoid repeating definitions for each dataset\n" +
                "#\n" +
                "# action=[REPLACE,APPEND,DELETE] (default:REPLACE)\n" +
                "# deleteAfterImport=[TRUE|FALSE] (default:FALSE)\n" +
                "\n" +
                "default.action=REPLACE\n" +
                "default.deleteAfterImport=FALSE\n" +
                "\n" +
                "# map a source tsv column (right side) to a property name or full propertyURI (left)\n" +
                "# predefined properties: ParticipantId, SiteId, VisitId, Created\n" +
                "default.property.ParticipantId=ptid\n" +
                "default.property.Created=dfcreate\n" +
                "\n" +
                "# use to map from filename->datasetid\n" +
                "# NOTE: if there are NO explicit import definitions, we will try to import all files matching pattern\n" +
                "# NOTE: if there are ANY explicit mapping, we will only import listed datasets\n" +
                "\n" +
                "default.filePattern=plate(\\\\d\\\\d\\\\d).tsv\n" +
                "default.importAllMatches=TRUE");
        writer.close();

        // Create dataset metadata file
        TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
        TablesDocument.Tables tablesXml = tablesDoc.addNewTables();

        for (DataSetDefinition def : datasets)
        {
            TableInfo ti = def.getTableInfo(ctx.getUser());
            List<ColumnInfo> allColumns = ti.getColumns();
            List<ColumnInfo> columns = getColumnsToExport(allColumns, false);

            // Write metadata
            TableType tableXml = tablesXml.addNewTable();
            tableXml.setTableName(def.getName());
            tableXml.setTableDbType("TABLE");
            TableType.Columns columnsXml = tableXml.addNewColumns();

            if (null == defaultDateFormat)
                defaultDateFormat = DateUtil.getStandardDateFormatString();

            for (ColumnInfo column : columns)
            {
                ColumnType columnXml = columnsXml.addNewColumn();
                columnXml.setColumnName(column.getColumnName());

                if (null != column.getDescription())
                    columnXml.setDescription(column.getDescription());

                if (!column.isNullable())
                    columnXml.setNullable(false);

                String formatString = column.getFormatString();

                // Write only if it's non-null (and in the case of dates, different from the global default)
                if (null != formatString && (!Date.class.isAssignableFrom(column.getJavaClass()) || !formatString.equals(defaultDateFormat)))
                    columnXml.setFormatString(formatString);

                if (null != column.getDefaultValue())
                    columnXml.setDefaultValue(column.getDefaultValue());

                if (null != column.getMvColumnName())
                    columnXml.setQcColumnName(column.getMvColumnName());  // TODO: Change name in tableinfo.xsd

                ForeignKey fk = column.getFk();

                if (null != fk && null != fk.getLookupColumnName())
                {
                    ColumnType.Fk fkXml = columnXml.addNewFk();
                    TableInfo tinfo = fk.getLookupTableInfo();
                    fkXml.setFkDbSchema(tinfo.getPublicSchemaName());
                    fkXml.setFkTable(tinfo.getPublicName());
                    fkXml.setFkColumnName(fk.getLookupColumnName());
                }

                // TODO: Field validators?
                // TODO: Default values
                // TODO: Lable
                // TODO: RangeURI

                String autoFill = column.getAutoFillValue();

                if (null != autoFill)
                    System.out.println(column.getName() + " autofill: " + autoFill);
            }

            // Write dataset
            ResultSet rs = Table.select(ti, columns, null, null);
            TSVGridWriter tsvWriter = new TSVGridWriter(rs);
            tsvWriter.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.propertyName);
            PrintWriter out = fs.getPrintWriter(def.getFileName());
            tsvWriter.write(out);     // NOTE: TSVGridWriter closes PrintWriter and ResultSet
        }

        XmlBeanUtil.saveDoc(fs.getPrintWriter(META_DATA_FILENAME), tablesDoc);
        dsXml.setMetaDataFile(META_DATA_FILENAME);
        XmlBeanUtil.saveDoc(fs.getPrintWriter(MANIFEST_FILENAME), manifestXml);
    }

    private static boolean shouldExport(ColumnInfo column)
    {
        return column.isUserEditable();
    }

    public static List<ColumnInfo> getColumnsToExport(List<ColumnInfo> inColumns, boolean includeAutoKey)
    {
        List<ColumnInfo> outColumns = new ArrayList<ColumnInfo>(inColumns.size());

        ColumnInfo ptidColumn = null; String ptidURI = DataSetDefinition.getParticipantIdURI();
        ColumnInfo sequenceColumn = null; String sequenceURI = DataSetDefinition.getSequenceNumURI();

        for (ColumnInfo in : inColumns)
        {
            if (in.getPropertyURI().equals(ptidURI))
            {
                if (null == ptidColumn)
                    ptidColumn = in;
                else
                    LOG.error("More than one ptid column found: " + ptidColumn.getName() + " and " + in.getName());
            }

            if (in.getPropertyURI().equals(sequenceURI))
            {
                if (null == sequenceColumn)
                    sequenceColumn = in;
                else
                    LOG.error("More than one sequence number column found: " + sequenceColumn.getName() + " and " + in.getName());
            }
        }

        for (ColumnInfo in : inColumns)
        {
            if (shouldExport(in) || (includeAutoKey && in.getName().equals("autokey")))
            {
                if ("visit".equalsIgnoreCase(in.getName()) && !in.equals(sequenceColumn))
                    continue;

                if ("ptid".equalsIgnoreCase(in.getName()) && !in.equals(ptidColumn))
                    continue;

                outColumns.add(in);
            }
        }

        return outColumns;
    }


    private void writeMetadata()
    {
        
    }
}
