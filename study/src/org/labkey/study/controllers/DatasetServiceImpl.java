/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

package org.labkey.study.controllers;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.QueryService;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import gwt.client.org.labkey.study.dataset.client.DatasetService;
import gwt.client.org.labkey.study.dataset.client.model.GWTDataset;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: jgarms
 */
@SuppressWarnings("unchecked")
class DatasetServiceImpl extends DomainEditorServiceBase implements DatasetService
{

    private final StudyImpl study;
    private final StudyManager studyManager;

    public DatasetServiceImpl(ViewContext context, StudyImpl study, StudyManager studyManager)
    {
        super(context);
        this.study = study;
        this.studyManager = studyManager;
    }


    public GWTDataset getDataset(int id)
    {
        try
        {
            DataSetDefinition dd = study.getDataSet(id);
            if (null == dd)
                return null;
            GWTDataset ds = new GWTDataset();
            PropertyUtils.copyProperties(ds, dd);
            ds.setDatasetId(dd.getDataSetId()); // upper/lowercase problem
            ds.setKeyPropertyManaged(dd.getKeyManagementType() != DataSet.KeyManagementType.None);

            List<CohortImpl> cohorts = StudyManager.getInstance().getCohorts(getContainer(), getUser());
            Map<String, String> cohortMap = new HashMap<>();
            if (cohorts != null && cohorts.size() > 0)
            {
                cohortMap.put("All", "");
                for (CohortImpl cohort : cohorts)
                    cohortMap.put(cohort.getLabel(), String.valueOf(cohort.getRowId()));
            }
            ds.setCohortMap(cohortMap);

            Map<String, String> visitDateMap = new HashMap<>();
            TableInfo tinfo = dd.getTableInfo(getUser(), false);
            for (ColumnInfo col : tinfo.getColumns())
            {
                if (!Date.class.isAssignableFrom(col.getJavaClass()))
                    continue;
                if (col.getName().equalsIgnoreCase("visitdate"))
                    continue;
                if (col.getName().equalsIgnoreCase("modified"))
                    continue;
                if (col.getName().equalsIgnoreCase("created"))
                    continue;
                if (visitDateMap.isEmpty())
                    visitDateMap.put("", "");
                visitDateMap.put(col.getName(), col.getName());
            }
            ds.setVisitDateMap(visitDateMap);


            ExpProtocol protocol = dd.getAssayProtocol();
            if (protocol != null)
            {
                ds.setSourceAssayName(protocol.getName());

                ActionURL assayURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(protocol.getContainer(), protocol);
                ds.setSourceAssayURL(assayURL.getLocalURIString());
            }


            return ds;
        }
        catch (Exception x)
        {
            throw UnexpectedException.wrap(x);
        }
    }


    public List<String> updateDatasetDefinition(GWTDataset ds, GWTDomain orig, GWTDomain update)
    {
        assert orig.getDomainURI().equals(update.getDomainURI());
        List<String> errors = new ArrayList<>();

        if (!checkCanUpdate(ds, errors))
            return errors;

        Domain d = PropertyService.get().getDomain(getContainer(), update.getDomainURI());
        if (null == d)
        {
            errors.add("Domain not found: " + update.getDomainURI());
            return errors;
        }

        if (!ds.getTypeURI().equals(orig.getDomainURI()) ||
            !ds.getTypeURI().equals(update.getDomainURI()))
        {
            errors.add("Illegal Argument");
            return errors;
        }

        // Remove any fields that are duplicates of the default dataset fields.
        // e.g. participantid, etc.

        List<GWTPropertyDescriptor> updatedProps = update.getFields();
        for (Iterator<GWTPropertyDescriptor> iter = updatedProps.iterator(); iter.hasNext();)
        {
            GWTPropertyDescriptor prop = iter.next();
            if (DataSetDefinition.isDefaultFieldName(prop.getName(), study))
                iter.remove();
        }
        update.setFields(updatedProps);

        errors = updateDomainDescriptor(orig, update);
        if (errors == null)
            errors = updateDataset(ds, orig.getDomainURI());

        return errors.isEmpty() ? null : errors;
    }

    private List updateDataset(GWTDataset ds, String domainURI)
    {
        List<String> errors = new ArrayList<>();
        try
        {
            // CONSIDER: optimistic concurrency validate against current
            // validate that this smells right
            DataSetDefinition def = study.getDataSet(ds.getDatasetId());
            if (null == def)
            {
                errors.add("Dataset not found");
                return errors;
            }

            if (ds.getDemographicData() && !def.isDemographicData() && !StudyManager.getInstance().isDataUniquePerParticipant(def))
            {
                errors.add("This dataset currently contains more than one row of data per " +  StudyService.get().getSubjectNounSingular(getContainer()) +
                        ". Demographic data includes one row of data per " + StudyService.get().getSubjectNounSingular(getContainer()) + ".");
                return errors;
            }

            if (ds.getDemographicData())
            {
                ds.setKeyPropertyName(null);
                ds.setKeyPropertyManaged(false);
            }

            DataSetDefinition updated = def.createMutable();
            // Clear the category ID so that it gets regenerated based on the new string - see issue 19649
            updated.setCategoryId(null);
            BeanUtils.copyProperties(updated, ds);

            // Default is no key management
            DataSet.KeyManagementType keyType = DataSet.KeyManagementType.None;
            String keyPropertyName = null;
            if (ds.getKeyPropertyName() != null)
            {
                Domain domain = PropertyService.get().getDomain(getContainer(), domainURI);

                for (DomainProperty dp : domain.getProperties())
                {
                    if (dp.getName().equalsIgnoreCase(ds.getKeyPropertyName()))
                    {
                        keyPropertyName = dp.getName();
                        // Be sure that the user really wants a managed key, not just that disabled select box still had a value
                        if (ds.getKeyPropertyManaged())
                        {
                            if (dp.getPropertyDescriptor().getPropertyType() == PropertyType.INTEGER || dp.getPropertyDescriptor().getPropertyType() == PropertyType.DOUBLE)
                            {
                                // Number fields must be RowIds
                                keyType = DataSet.KeyManagementType.RowId;
                            }
                            else if (dp.getPropertyDescriptor().getPropertyType() == PropertyType.STRING)
                            {
                                // Strings can be managed as GUIDs
                                keyType = DataSet.KeyManagementType.GUID;
                            }
                            else
                            {
                                throw new IllegalStateException("Unsupported column type for managed keys: " + dp.getPropertyDescriptor().getPropertyType());
                            }
                        }
                        break;
                    }
                }
            }
            updated.setKeyPropertyName(keyPropertyName);
            updated.setKeyManagementType(keyType);

            if (!def.getLabel().equals(updated.getLabel()))
            {
                DataSet existing = studyManager.getDataSetDefinitionByLabel(study, updated.getLabel());
                if (existing != null && existing.getDataSetId() != ds.getDatasetId())
                {
                    errors.add("A Dataset already exists with the label \"" + updated.getLabel() +"\"");
                    return errors;
                }
            }

            if (!def.getName().equals(updated.getName()))
            {
                // issue 17766: check if dataset or query exist with this name
                DataSet existing = studyManager.getDataSetDefinitionByName(study, updated.getName());
                if ((null != existing && existing.getDataSetId() != ds.getDatasetId())
                    || null != QueryService.get().getQueryDef(getUser(), getContainer(), "study", updated.getName()))
                {
                    errors.add("A Dataset or Query already exists with the name \"" + updated.getName() +"\"");
                    return errors;
                }
            }

            studyManager.updateDataSetDefinition(getUser(), updated, errors);

            return errors;
        }
        catch (SQLException e)
        {
            errors.add("Additional key column must have unique values.");
            return errors;
        }
        catch (Exception x)
        {
            throw UnexpectedException.wrap(x);
        }
    }

    public List updateDatasetDefinition(GWTDataset ds, GWTDomain domain, String tsv)
    {
        try
        {
            List<String> errors = new ArrayList<>();

            if (!checkCanUpdate(ds, errors))
                return errors;

            List<Map<String, Object>> maps = null;
            if (null != tsv && tsv.length() > 0)
            {
                TabLoader loader = new TabLoader(tsv, true);
                maps = loader.load();
            }

            try
            {
                boolean success = OntologyManager.importOneType(domain.getDomainURI(), maps, errors, getContainer(), getUser());
                if (!success)
                    errors.add("No properties were successfully imported.");

                if (errors.isEmpty())
                    errors = updateDataset(ds, domain.getDomainURI());
            }
            catch (ChangePropertyDescriptorException e)
            {
                errors.add(e.getMessage() == null ? e.toString() : e.getMessage());
            }

            return errors.isEmpty() ? null : errors;
        }
        catch (IOException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }


    private boolean checkCanUpdate(GWTDataset ds, List<String> errors)
    {
        if (!getContainer().hasPermission(getUser(), AdminPermission.class))
        {
            errors.add("Unauthorized");
            return false;
        }
        Study study = StudyService.get().getStudy(getContainer());
        if (null == study)
        {
            errors.add("Study not found in current container");
            return false;
        }
        DataSetDefinition def = (DataSetDefinition)study.getDataSet(ds.getDatasetId());
        if (null == def)
        {
            errors.add("Dataset not found");
            return false;
        }
        if (!def.canUpdateDefinition(getUser()))
        {
            errors.add("Shared dataset can not be edited in this folder.");
            return false;
        }
        return true;
    }


    @Override
    public GWTDomain getDomainDescriptor(String typeURI, String domainContainerId)
    {
        GWTDomain domain = super.getDomainDescriptor(typeURI, domainContainerId);
        domain.setDefaultValueOptions(new DefaultValueType[]
                { DefaultValueType.FIXED_EDITABLE, DefaultValueType.LAST_ENTERED }, DefaultValueType.FIXED_EDITABLE);
        return domain;
    }


    @Override
    public GWTDomain getDomainDescriptor(String typeURI)
    {
        GWTDomain domain = super.getDomainDescriptor(typeURI);
        domain.setDefaultValueOptions(new DefaultValueType[]
                { DefaultValueType.FIXED_EDITABLE, DefaultValueType.LAST_ENTERED }, DefaultValueType.FIXED_EDITABLE);
        return domain;
    }
}
