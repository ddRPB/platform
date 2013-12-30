<%
/*
 * Copyright (c) 2005-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.exp.ExperimentDataHandler" %>
<%@ page import="org.labkey.api.exp.api.ExpData" %>
<%@ page import="org.labkey.api.exp.api.ExpMaterial" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocolApplication" %>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ExpRun> me = (JspView<ExpRun>) HttpView.currentView();
    ExpRun run = me.getModelBean();
    Container c = getContainer();
%>

<table class="labkey-protocol-applications">
    <tr>
        <td><b>Name</b></td>
        <td><b>Inputs</b></td>
        <td><b>Outputs</b></td>
    </tr>
    <% for (ExpProtocolApplication protocolApplication : run.getProtocolApplications()) { %>
        <tr>
            <td valign="top"><a href="showApplication.view?rowId=<%= protocolApplication.getRowId() %>"><%= h(protocolApplication.getName()) %></a></td>
            <td valign="top">
                <% for (ExpMaterial material : protocolApplication.getInputMaterials()) { %>
                    <a href="showMaterial.view?rowId=<%= material.getRowId() %>"><%= h(material.getName()) %></a><br>
                <% } %>
                <% for (ExpData data : protocolApplication.getInputDatas()) { %>
                    <a href="showData.view?rowId=<%= data.getRowId() %>"><%= h(data.getName()) %></a>
                <%
                    ExperimentDataHandler handler = data.findDataHandler();
                    ActionURL url = handler == null ? null : handler.getContentURL(c, data);
                    if (url != null) { %><%=textLink("view", url)%><% } %><br/>
                <% } %>
            </td>
            <td valign="top">
                <% for (ExpMaterial material : protocolApplication.getOutputMaterials()) { %>
                    <a href="showMaterial.view?rowId=<%= material.getRowId() %>"><%= h(material.getName()) %></a><br>
                <% } %>
                <% for (ExpData data : protocolApplication.getOutputDatas()) { %>
                    <a href="showData.view?rowId=<%= data.getRowId() %>"><%= h(data.getName()) %></a>
                <%
                    ExperimentDataHandler handler = data.findDataHandler();
                    ActionURL url = handler == null ? null : handler.getContentURL(c, data);
                    if (url != null) { %><%=textLink("view", url)%><% } %><br/>
                <% } %>
            </td>
        </tr>
    <% } %>
</table>