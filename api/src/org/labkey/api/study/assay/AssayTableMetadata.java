/*
 * Copyright (c) 2009-2011 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.study.TimepointType;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.util.Pair;

/**
 * User: jeckels
 * Date: May 11, 2009
 */
public class AssayTableMetadata
{
    private final FieldKey _runFieldKey;
    private final FieldKey _resultRowIdFieldKey;
    private final FieldKey _specimenDetailParentFieldKey;
    /** The name of the property in the dataset that points back to the RowId-type column in the assay's data table */
    private final String _datasetRowIdPropertyName;

    public AssayTableMetadata(FieldKey specimenDetailParentFieldKey, FieldKey runFieldKey, FieldKey resultRowIdFieldKey)
    {
        this(specimenDetailParentFieldKey, runFieldKey, resultRowIdFieldKey, resultRowIdFieldKey.getName());
    }

    public AssayTableMetadata(FieldKey specimenDetailParentFieldKey, FieldKey runFieldKey, FieldKey resultRowIdFieldKey, String datasetRowIdPropertyName)
    {
        _runFieldKey = runFieldKey;
        _resultRowIdFieldKey = resultRowIdFieldKey;
        _specimenDetailParentFieldKey = specimenDetailParentFieldKey;
        _datasetRowIdPropertyName = datasetRowIdPropertyName;
    }

    public FieldKey getSpecimenDetailParentFieldKey()
    {
        return _specimenDetailParentFieldKey;
    }

    public FieldKey getParticipantIDFieldKey()
    {
        return new FieldKey(_specimenDetailParentFieldKey, AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME);
    }

    public FieldKey getSpecimenIDFieldKey()
    {
        return new FieldKey(_specimenDetailParentFieldKey, AbstractAssayProvider.SPECIMENID_PROPERTY_NAME);
    }

    /** @return the name of the property in the dataset that points back to the RowId-type column in the assay's data table */
    public String getDatasetRowIdPropertyName()
    {
        return _datasetRowIdPropertyName;
    }

    public FieldKey getVisitIDFieldKey(TimepointType timepointType)
    {
        if (timepointType == TimepointType.DATE)
        {
            return new FieldKey(_specimenDetailParentFieldKey, AbstractAssayProvider.DATE_PROPERTY_NAME);
        }
        else if (timepointType == TimepointType.VISIT)
        {
            return new FieldKey(_specimenDetailParentFieldKey, AbstractAssayProvider.VISITID_PROPERTY_NAME);
        }
        else
        {
            return null;
        }
    }

    public FieldKey getRunFieldKeyFromResults()
    {
        return _runFieldKey;
    }

    /** @return relative to the assay's results table, the FieldKey that gets to the Run table */
    public FieldKey getRunRowIdFieldKeyFromResults()
    {
        return new FieldKey(_runFieldKey, ExpRunTable.Column.RowId.toString());
    }

    public FieldKey getResultRowIdFieldKey()
    {
        return _resultRowIdFieldKey;
    }

    /**
     * Get the FieldKey to the TargetStudy column relative to the results table.  The assay instance
     * may define the TargetStudy column on the results table itself, the run table, or the batch table.
     *
     * @param provider provider
     * @param protocol protocol
     * @return relative to the assay's results table, the FieldKey that gets to the TargetStudy
     */
    public FieldKey getTargetStudyFieldKey(AssayProvider provider, ExpProtocol protocol)
    {
        Pair<ExpProtocol.AssayDomainTypes, DomainProperty> pair = provider.findTargetStudyProperty(protocol);
        if (pair == null)
            return null;

        switch (pair.first)
        {
            case Result:
                return getTargetStudyFieldKeyOnResults();

            case Run:
                return getTargetStudyFieldKeyOnRun();

            case Batch:
            default:
                return getTargetStudyFieldKeyOnBatch();
        }
    }

    protected FieldKey getTargetStudyFieldKeyOnResults()
    {
        return new FieldKey(null, AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME);
    }

    protected FieldKey getTargetStudyFieldKeyOnRun()
    {
        FieldKey runFK = getRunFieldKeyFromResults();
        return new FieldKey(runFK, AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME);
    }

    protected FieldKey getTargetStudyFieldKeyOnBatch()
    {
        FieldKey batchFK = new FieldKey(getRunRowIdFieldKeyFromResults().getParent(), AssayService.BATCH_COLUMN_NAME);
        return new FieldKey(batchFK, AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME);
    }
    
}
