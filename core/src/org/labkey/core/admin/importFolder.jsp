<%
/*
 * Copyright (c) 2012 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.gwt.client.ui.LabKeyLinkHTML" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    Container c = getViewContext().getContainerNoTab();
    String requestOrigin;
    if(request.getParameter("origin") != null){
        requestOrigin = request.getParameter("origin");
    }
    else {
        requestOrigin = "here";
    }
%>

<script>
    console.log(<%=q(requestOrigin)%>);
</script>

<form action="" name="import" enctype="multipart/form-data" method="post">
<table cellpadding=0>
    <%=formatMissedErrorsInTable("form", 2)%>

<tr><td>
    <% if(requestOrigin.equals("Study")) {%>
    You can import a study archive, or a folder containing a study, to create and populate a new study.  A study archive is a .study.zip file or a collection of
    individual files that conforms to the LabKey study export conventions and formats.  In most cases, a study archive is created
    using the study export feature.  Using export and import, a study can be moved from one server to another or a new study can
    be created using a standard template.
    <p>
        <span style='font-style: italic;'>Notice: study archives have been deprecated in favor of folder archives.  Files will be exported as folder archives.
    </p>
    <% } else if(requestOrigin.equals("Reload")){%>
    You can reload a folder archive to update an existing study with new settings and data.  A folder archive is a .folder.zip file
    or a collection of individual files that comforms to the LabKey study export conventions and formats.  A folder archive can be
    created using the study export feature or via scripts that write data from a master repository into the correct formats.  You may also reload using a
    study archive, which has the format .study.zip
    Note: Reloading a study will replace existing study data with the data in the archive.
    <% } else {%>

You can import a folder archive to create and populate a new folder.  A folder archive is a .folder.zip file or a collection of
individual files that comforms to the LabKey folder export conventions and formats.  In most cases, a folder archive is created
using the folder export feature.  Using export and import, a folder can be moved from one server to another or a new folder can
be created using a standard template. You can also populate a new folder from a template folder on the current server using the "Create
Folder From Template" option from the folder creation page.
<% } %>
<%--<p>For more information about exporting, importing, and reloading folders, see <%=helpLink("importExportFolder", "the folder documentation")%>.</p>--%>
</td></tr>
<% if(requestOrigin.equals("Study")){%>
    <tr><td class="labkey-announcement-title" align=left><span>Import Study From Local Zip Archive</span></td></tr>
    <tr><td class="labkey-title-area-line"></td></tr>
    <tr><td>To import a study from a zip archive on your local machine (for example, a folder that you have exported and saved
        to your local hard drive), browse to a .folder.zip archive, open it, and click the "Import Study From Local Zip Archive" button below.</td></tr>
    <tr><td><input type="file" name="folderZip" size="50"></td></tr>
    <tr>
        <td><%=generateSubmitButton("Import Study From Local Zip Archive")%></td>

<% } else if(requestOrigin.equals("Reload")){%>
    <tr><td class="labkey-announcement-title" align=left><span>Reload Study From Local Zip Archive</span></td></tr>
    <tr><td class="labkey-title-area-line"></td></tr>
    <tr><td>To reload a study from a zip archive on your local machine (for example, a folder that you have exported and saved
        to your local hard drive), browse to a .folder.zip archive, open it, and click the "Reload Study From Local Zip Archive" button below.</td></tr>
    <tr><td><input type="file" name="folderZip" size="50"></td></tr>
    <tr>
        <td><%=generateSubmitButton("Reload Study From Local Zip Archive")%></td>

<% } else if(requestOrigin.equals("here")) { %>
    <tr><td class="labkey-announcement-title" align=left><span>Import Folder From Local Zip Archive</span></td></tr>
    <tr><td class="labkey-title-area-line"></td></tr>
    <tr><td>To import a folder from a zip archive on your local machine (for example, a folder that you have exported and saved
        to your local hard drive), browse to a .folder.zip archive, open it, and click the "Import Folder From Local Zip Archive" button below.</td></tr>
    <tr><td><input type="file" name="folderZip" size="50"></td></tr>
    <tr>
        <td><%=generateSubmitButton("Import Folder From Local Zip Archive")%></td>
<% } %>


</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<% if(requestOrigin.equals("Study")){%>
    <tr><td class="labkey-announcement-title" align=left><span>Import Study From Server-Accessible Archive</span></td></tr>
    <tr><td class="labkey-title-area-line"></td></tr>
        <tr><td>
            To import a study from a server-accessible archive, click the "Import Study Using Pipeline"
            button below, navigate to a .folder.zip archive or a study.xml file, and click the "Import Data" button.
        </td></tr>
    <tr>
        <td><%=generateButton("Import Study Using Pipeline", urlProvider(PipelineUrls.class).urlBrowse(c, "pipeline"))%></td>
    </tr>

<% } else if(requestOrigin.equals("Reload")) { %>
    <tr><td class="labkey-announcement-title" align=left><span>Reload Folder From Server-Accessible Archive</span></td></tr>
    <tr><td class="labkey-title-area-line"></td></tr>
    <tr><td>
        To reload a folder from a server-accessible archive, click the "Reload Folder Using Pipeline"
        button below, navigate to a .folder.zip (or .study.xml) archive file, and click the "Import Data" button.
    </td></tr>
    <tr>
        <td><%=generateButton("Reload Folder Using Pipeline", urlProvider(PipelineUrls.class).urlBrowse(c, "pipeline"))%></td>
    </tr>

<% } else if(requestOrigin.equals("here")) { %>
    <tr><td class="labkey-announcement-title" align=left><span>Import Folder From Server-Accessible Archive</span></td></tr>
    <tr><td class="labkey-title-area-line"></td></tr>
    <tr><td>
        To import a folder from a server-accessible archive, click the "Import Folder Using Pipeline"
        button below, navigate to a .folder.zip archive file, and click the "Import Data" button.
    </td></tr>
    <tr>
        <td><%=generateButton("Import Folder Using Pipeline", urlProvider(PipelineUrls.class).urlBrowse(c, "pipeline"))%></td>
    </tr>
<% } %>

<tr>
    <td>&nbsp;</td>
</tr>
</table>
</form>

