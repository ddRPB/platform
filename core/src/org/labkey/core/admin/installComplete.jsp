<%
/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    AdminUrls adminURLs = PageFlowUtil.urlProvider(AdminUrls.class);
    ViewContext viewContext = HttpView.currentContext();

    // Build links for what's new and the release notes
    // We use most recent "major" version of the core module, so versions 11.20, 11.21, 11.29, etc will all go to the
    // 11.2 docs.
    double coreVersion = org.labkey.api.module.ModuleLoader.getInstance().getCoreModule().getVersion();
    String versionSuffix = Double.toString(Math.floor(coreVersion * 10.0) / 10.0).replace(".", "");
    boolean newInstall = org.labkey.api.module.ModuleLoader.getInstance().isNewInstall();
%>
<p>Congratulations! Your LabKey Server installation <%=text(newInstall ? "is ready to use" : "has been successfully upgraded")%>.</p>

<p>What would you like to do next?</p>

<ul>
    <% if (newInstall) { %>
        <li style="margin-bottom: 10px;">
            <a href="<%= h(adminURLs.getCreateProjectURL(viewContext.getActionURL())) %>">Create a new project</a> as place to
            <ul>
                <li>perform mass spectrometry-based proteomics (MS1 and MS2)
                <li>analyze flow cytometry data</li>
                <li>import plate-based or other types of assay data</li>
                <li>organize data in a longitudinal study</li>
                <li>collaborate with tools like messages boards, wikis, and issue trackers</li>
            </ul>
        </li>

        <li style="margin-bottom: 10px;">
            <a href="<%= h(adminURLs.getAdminConsoleURL()) %>">Customize LabKey Server</a>
            (<a href="<%= h(adminURLs.getProjectSettingsURL(org.labkey.api.data.ContainerManager.getRoot())) %>">appearance</a>,
            security, <a href="<%= h(adminURLs.getCustomizeSiteURL()) %>">site settings</a>, etc)
        </li>

    <% } %>
    
    <li style="margin-bottom: 10px;">
        <a href="<%= h(ContainerManager.getHomeContainer().getStartURL(getUser())) %>">Go directly to the server's Home page</a>
    </li>

    <li style="margin-bottom: 10px;">
        <%=helpLink("whatsnew" + versionSuffix, "Learn what's new in this version of LabKey Server")%>
    </li>

    <li style="margin-bottom: 10px;">
        <%=helpLink("releaseNotes" + versionSuffix, "Read the full release notes for this version of LabKey Server")%>
    </li>
</ul>