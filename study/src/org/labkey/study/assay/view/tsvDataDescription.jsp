<%
/*
 * Copyright (c) 2007-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.property.DomainProperty"%>
<%@ page import="org.labkey.api.study.actions.AssayRunUploadForm" %>
<%@ page import="org.labkey.api.study.actions.TemplateAction" %>
<%@ page import="org.labkey.api.study.assay.AssayUrls" %>
<%@ page import="org.labkey.api.study.assay.PipelineDataCollector" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AssayRunUploadForm> me = (JspView<AssayRunUploadForm>) HttpView.currentView();
    AssayRunUploadForm bean = me.getModelBean();
%>

<table>
    <tr>
        <td colspan="2">Expected Columns:
            <table>
        <%
            for (DomainProperty pd : bean.getRunDataProperties())
            {
        %>
            <tr><td><strong><%= pd.getName() %><%= (pd.isRequired() ? " (Required)" : "") %></strong>:</td><td><%= pd.getPropertyDescriptor().getPropertyType().getXarName() %></td><td><%=h(pd.getDescription())%></td></tr>
        <%
            }
        %>
            </table>
            <% if (PipelineDataCollector.getFileQueue(bean).isEmpty())
            { %>
                <%= textLink("download spreadsheet template",
                    urlProvider(AssayUrls.class).getProtocolURL(bean.getContainer(), bean.getProtocol(), TemplateAction.class))%>
                <br>After downloading and editing the spreadsheet template, paste it into the text area below or save the spreadsheet and upload it as a file.
            <% }%>
        </td>
    </tr>
</table>