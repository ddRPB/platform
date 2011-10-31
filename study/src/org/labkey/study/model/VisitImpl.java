/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.study.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.Visit;
import org.labkey.study.StudySchema;

import java.util.List;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.io.Serializable;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:28:55 AM
 */
public class VisitImpl extends AbstractStudyEntity<VisitImpl> implements Cloneable, Serializable, Visit
{
    // standard strings to use in URLs etc
    public static final String VISITKEY = "visitRowId";
    public static final String SEQUENCEKEY = "sequenceNum";
    public static final double DEMOGRAPHICS_VISIT = -1;

    private int _rowId = 0;
//    private String _name = null;
    private double _sequenceMin = 0;
    private double _sequenceMax = 0;
    private Character _typeCode;
    private Integer _visitDateDatasetid = 0;
    private Integer _cohortId;
    private int _chronologicalOrder;
    
    public VisitImpl()
    {
    }


    public VisitImpl(Container container, double seq, String label, Type type)
    {
        this(container, seq, label, null == type ? null : type.getCode());
    }


    public VisitImpl(Container container, double seqMin, String label, Character typeCode)
    {
        this(container, seqMin, seqMin, label, typeCode);
    }


    public VisitImpl(Container container, double seqMin, double seqMax, String label, Type type)
    {
        this(container, seqMin, seqMax, label, null == type ? null : type.getCode());
    }


    public VisitImpl(Container container, double seqMin, double seqMax, String name, Character typeCode)
    {
        setContainer(container);
        _sequenceMin = seqMin;
        _sequenceMax = seqMax;
//        _name = name;
        _label = name;
        _typeCode = typeCode;
        _showByDefault = true;
    }


//    public String getName()
//    {
//        if (_name == null && _label == null)
//        {
//            if (_sequenceMax == 0 && _sequenceMin == 0)
//                return null;
//            return getSequenceString();
//        }
//        return _name == null ? _label : _name;
//    }


    public String getSequenceString()
    {
        if (_sequenceMin == _sequenceMax)
            return VisitImpl.formatSequenceNum(_sequenceMin);
        else
            return VisitImpl.formatSequenceNum(_sequenceMin) + "-" + VisitImpl.formatSequenceNum(_sequenceMax);
    }


//    public void setName(String name)
//    {
//        _name = name;
//    }


    public String getDisplayString()
    {
        if (getLabel() != null)
            return getLabel();
        return getSequenceString();
    }


    public void addVisitFilter(SimpleFilter filter)
    {
        filter.addCondition("VisitRowId", getRowId());
    }


    public Character getTypeCode()
    {
        return _typeCode;
    }


    public void setTypeCode(Character typeCode)
    {
        verifyMutability();
        _typeCode = typeCode;
    }


    public Type getType()
    {
        if (_typeCode == null)
            return null;
        return Type.getByCode(_typeCode.charValue());
    }


    public Integer getVisitDateDatasetId()
    {
        return _visitDateDatasetid;
    }

    
    public void setVisitDateDatasetId(Integer visitDateDatasetId)
    {
        _visitDateDatasetid = visitDateDatasetId == null ? 0 : visitDateDatasetId.intValue();
    }


    public List<VisitDataSet> getVisitDataSets()
    {
        return StudyManager.getInstance().getMapping(this);
    }


    public Object getPrimaryKey()
    {
        return getRowId();
    }

    public double getSequenceNumMin()
    {
        return _sequenceMin;
    }

    public void setSequenceNumMin(double sequenceMin)
    {
        this._sequenceMin = sequenceMin;
    }

    public double getSequenceNumMax()
    {
        return _sequenceMax;
    }

    public void setSequenceNumMax(double sequenceMax)
    {
        this._sequenceMax = sequenceMax;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        assert this._rowId == 0;
        this._rowId = rowId;
    }

    // only 4 scale digits
    public static double parseSequenceNum(String s)
    {
        double d = Double.parseDouble(s);
        return Math.round(d*10000) / 10000.0;
    }


    // only 4 scale digits
    static NumberFormat sequenceFormat = new DecimalFormat("0.####");

    public static String formatSequenceNum(double d)
    {
        d = Math.round(d*10000) / 10000.0;
        return sequenceFormat.format(d);
    }

    public Integer getCohortId()
    {
        return _cohortId;
    }

    public void setCohortId(Integer cohortId)
    {
        _cohortId = cohortId;
    }

    public Cohort getCohort()
    {
        if (_cohortId == null)
            return null;
        return Table.selectObject(StudySchema.getInstance().getTableInfoCohort(), _cohortId, CohortImpl.class);
    }

    public int getChronologicalOrder()
    {
        return _chronologicalOrder;
    }

    public void setChronologicalOrder(int chronologicalOrder)
    {
        _chronologicalOrder = chronologicalOrder;
    }
}
