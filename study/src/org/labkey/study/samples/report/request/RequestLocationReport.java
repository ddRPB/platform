/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
package org.labkey.study.samples.report.request;

import org.labkey.api.data.SimpleFilter;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.samples.report.SpecimenVisitReportParameters;
import org.labkey.study.samples.report.SpecimenTypeVisitReport;
import org.labkey.study.query.SpecimenQueryView;
import org.labkey.study.SampleManager;

/**
 * User: brittp
 * Created: Jan 14, 2008 1:37:24 PM
 */
public class RequestLocationReport extends SpecimenTypeVisitReport
{
    private int _locationId;
    private boolean _completeRequestsOnly;

    public RequestLocationReport(String titlePrefix, SimpleFilter filter, SpecimenVisitReportParameters parameters, VisitImpl[] visits, int locationId, boolean completeRequestsOnly)
    {
        super(titlePrefix, visits, filter, parameters);
        _locationId = locationId;
        _completeRequestsOnly = completeRequestsOnly;
    }

    protected String getFilterQueryString(VisitImpl visit, SampleManager.SummaryByVisitType summary)
    {
        return super.getFilterQueryString(visit, summary)  + "&" +
                (_completeRequestsOnly ? SpecimenQueryView.PARAMS.showCompleteRequestedBySite : SpecimenQueryView.PARAMS.showRequestedBySite) + "=" + _locationId;
    }
}