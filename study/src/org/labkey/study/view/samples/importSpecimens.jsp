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
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController"%>
<%@ page import="org.labkey.study.pipeline.SpecimenBatch"%>
<%@ page import="java.util.List"%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpringSpecimenController.ImportSpecimensBean> me =
            (JspView<SpringSpecimenController.ImportSpecimensBean>) HttpView.currentView();
    SpringSpecimenController.ImportSpecimensBean bean = me.getModelBean();
    boolean hasError = !bean.getErrors().isEmpty();
    List<SpecimenBatch.EntryDescription> entries = bean.getBatch().getEntryDescriptions();
%>
Specimen archive <b><%= bean.getBatch().getDefinitionFile().getName() %></b> contains the following files:<br><br>
<table class="labkey-data-region labkey-show-borders">
    <colgroup><col><col></colgroup>
    <%
        int row = 0;
        for (SpecimenBatch.EntryDescription entry : entries)
        {
    %>
        <tr class="<%= row++ % 2 == 1 ? "labkey-row" : "labkey-alternate-row"%>">
            <td class="labkey-row-header" align="left"><%= h(entry.getName()) %></td>
            <td>
                <table>
                    <tr>
                        <th align="right" style="border: 0px">Size</th>
                        <td><%= entry.getSize() / 1000 %> kb</td>
                    </tr>
                    <tr>
                        <th align="right" style="border: 0px">Modified</th>
                        <td><%= h(formatDateTime(entry.getDate())) %></td>
                    </tr>
                </table>
            </td>
        </tr>
    <%
        }
    %>
</table><br>

<div>
    <%
        if (hasError)
        {
            for (String error : bean.getErrors())
            {
    %>
            <br><font class=labkey-error><%= h(error) %></font>
    <%
            }
        }
        else
        {
            if (bean.isPreviouslyRun())
            {
    %>
    <span class="labkey-error">WARNING: A file by this name appears to have been previously imported.</span><br>
    To import a file by this name, the old log file must be deleted.<br><br>
    <a href="<%= ActionURL.toPathString("Pipeline-Status", "showList", bean.getContainer())%>">
        Click here</a> to view previous pipeline runs.<br><br>

        <form action="importSpecimenData.post" method=POST>
            <input type="hidden" name="deleteLogfile" value="true">
            <input type="hidden" name="path" value="<%= h(bean.getPath())%>">
            <%= generateSubmitButton("Delete logfile")%>&nbsp;<%= generateButton("Cancel", ActionURL.toPathString("Pipeline", "begin", bean.getContainer()))%>
        </form>
    <%
            }
            else
            {
    %>
        <form action="submitSpecimenImport.post" method=POST>
            <input type="hidden" name="path" value="<%= h(bean.getPath())%>">
            <%= generateSubmitButton("Start Import")%>
        </form>
    <%
            }
        }
    %>
</div>
