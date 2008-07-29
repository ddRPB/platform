<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.reports.ReportsController"%>
<%@ page import="org.labkey.study.model.StudyManager"%>
<%@ page import="org.labkey.study.model.DataSetDefinition"%>
<%@ page import="org.labkey.study.model.Visit"%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ReportsController.CreateCrosstabBean> me = (JspView<ReportsController.CreateCrosstabBean>) HttpView.currentView();
    org.labkey.study.controllers.reports.ReportsController.CreateCrosstabBean bean = me.getModelBean();

%>
<form action="participantCrosstab.view" method="GET">
<table>
    <tr>
        <td>Dataset</td>
        <td>
            <select name="datasetId">
                <%
                    for (DataSetDefinition dataset : bean.getDatasets())
                    {
                %>
                <option value="<%= dataset.getRowId() %>"><%= h(dataset.getDisplayString()) %></option>
                <%
                    }
                %>
            </select>
        </td>
    </tr>
    <tr>
        <td>Visit</td>
        <td>
            <select name="<%=Visit.VISITKEY%>">
                <option value="0">All Visits</option>
                <%
                    for (Visit visit : bean.getVisits())
                    {
                %>
                <option value="<%= visit.getRowId() %>"><%= h(visit.getDisplayString()) %></option>
                <%
                    }
                %>
            </select>
        </td>
    </tr>
    <tr>
        <td></td>
        <td>
            <%= buttonImg("Next") %>
            <%= buttonLink("Cancel", "begin.view") %>
        </td>
    </tr>
</table>
</form>