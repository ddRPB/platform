/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewForm;
import org.labkey.study.model.VisitImpl;
import org.springframework.validation.Errors;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * User: cnathe
 * Date: 1/16/14
 */
public class VisitForm extends ViewForm
{
    private int[] _dataSetIds;
    private String[] _dataSetStatus;
    private Double _sequenceNumMin;
    private Double _sequenceNumMax;
    private Double _protocolDay;
    private Character _typeCode;
    private boolean _showByDefault;
    private Integer _cohortId;
    private String _label;
    private String _description;
    private VisitImpl _visit;
    private int _visitDateDatasetId;
    private String _sequenceNumHandling;
    private boolean _reshow;

    public VisitForm()
    {
    }

    public void validate(Errors errors, Study study)
    {
        if (study.getTimepointType() == TimepointType.CONTINUOUS)
            errors.reject(null, "Unsupported operation for continuous date study");

        HttpServletRequest request = getRequest();

        if (null != StringUtils.trimToNull(request.getParameter(".oldValues")))
        {
            try
            {
                _visit = (VisitImpl) PageFlowUtil.decodeObject(request.getParameter(".oldValues"));
            }
            catch (IOException x)
            {
                throw new RuntimeException(x);
            }
        }

        //check for null min/max sequence numbers
        if (null == getSequenceNumMax() && null == getSequenceNumMin())
            errors.reject(null, "You must specify at least a minimum or a maximum value for the visit range.");

        //if min is null but max is not, set min to max and vice-versa
        if (null == getSequenceNumMin() && null != getSequenceNumMax())
            setSequenceNumMin(getSequenceNumMax());
        if (null == getSequenceNumMax() && null != getSequenceNumMin())
            setSequenceNumMax(getSequenceNumMin());

        // if target sequence num is null, set to min
        if (null == getProtocolDay() && TimepointType.DATE == study.getTimepointType())
            setProtocolDay(VisitImpl.calcDefaultDateBasedProtocolDay(getSequenceNumMin(), getSequenceNumMax()));

        VisitImpl visit = getBean();
        if (visit.getSequenceNumMin() > visit.getSequenceNumMax())
        {
            errors.reject(null, "The minimum value cannot be greater than the maximum value for the visit range.");
/*
                double min = visit.getSequenceNumMax();
                double max = visit.getSequenceNumMin();
                visit.setSequenceNumMax(max);
                visit.setSequenceNumMin(min);
*/
        }
        setBean(visit);
    }

    public VisitImpl getBean()
    {
        if (null == _visit)
            _visit = new VisitImpl();

        _visit.setContainer(getContainer());

        if (getTypeCode() != null)
            _visit.setTypeCode(getTypeCode());

        _visit.setLabel(getLabel());
        _visit.setDescription(getDescription());

        if (null != getSequenceNumMax())
            _visit.setSequenceNumMax(getSequenceNumMax());
        if (null != getSequenceNumMin())
            _visit.setSequenceNumMin(getSequenceNumMin());
        if (null != getProtocolDay())
            _visit.setProtocolDay(getProtocolDay());

        _visit.setCohortId(getCohortId());
        _visit.setVisitDateDatasetId(getVisitDateDatasetId());

        _visit.setSequenceNumHandling(getSequenceNumHandling());
        _visit.setShowByDefault(isShowByDefault());

        return _visit;
    }

    public void setBean(VisitImpl bean)
    {
        if (0 != bean.getSequenceNumMax())
            setSequenceNumMax(bean.getSequenceNumMax());
        if (0 != bean.getSequenceNumMin())
            setSequenceNumMin(bean.getSequenceNumMin());
        if (null != bean.getProtocolDay())
            setProtocolDay(bean.getProtocolDay());
        if (null != bean.getType())
            setTypeCode(bean.getTypeCode());
        setLabel(bean.getLabel());
        setCohortId(bean.getCohortId());
        setSequenceNumHandling(bean.getSequenceNumHandling());
    }

    public String[] getDataSetStatus()
    {
        return _dataSetStatus;
    }

    public void setDataSetStatus(String[] dataSetStatus)
    {
        _dataSetStatus = dataSetStatus;
    }

    public int[] getDataSetIds()
    {
        return _dataSetIds;
    }

    public void setDataSetIds(int[] dataSetIds)
    {
        _dataSetIds = dataSetIds;
    }

    public Double getSequenceNumMin()
    {
        return _sequenceNumMin;
    }

    public void setSequenceNumMin(Double sequenceNumMin)
    {
        _sequenceNumMin = sequenceNumMin;
    }

    public Double getSequenceNumMax()
    {
        return _sequenceNumMax;
    }

    public void setSequenceNumMax(Double sequenceNumMax)
    {
        _sequenceNumMax = sequenceNumMax;
    }

    public Double getProtocolDay()
    {
        return _protocolDay;
    }

    public void setProtocolDay(Double protocolDay)
    {
        _protocolDay = protocolDay;
    }

    public Character getTypeCode()
    {
        return _typeCode;
    }

    public void setTypeCode(Character typeCode)
    {
        _typeCode = typeCode;
    }

    public boolean isShowByDefault()
    {
        return _showByDefault;
    }

    public void setShowByDefault(boolean showByDefault)
    {
        this._showByDefault = showByDefault;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        this._label = label;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        this._description = description;
    }

    public Integer getCohortId()
    {
        return _cohortId;
    }

    public void setCohortId(Integer cohortId)
    {
        _cohortId = cohortId;
    }

    public int getVisitDateDatasetId()
    {
        return _visitDateDatasetId;
    }

    public void setVisitDateDatasetId(int visitDateDatasetId)
    {
        _visitDateDatasetId = visitDateDatasetId;
    }

    public String getSequenceNumHandling()
    {
        return _sequenceNumHandling;
    }

    public void setSequenceNumHandling(String sequenceNumHandling)
    {
        _sequenceNumHandling = sequenceNumHandling;
    }

    public boolean isReshow()
    {
        return _reshow;
    }

    public void setReshow(boolean reshow)
    {
        _reshow = reshow;
    }
}
