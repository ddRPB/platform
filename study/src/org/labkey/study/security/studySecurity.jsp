<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.util.HelpTopic"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.study.model.Study"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    HttpView<Study> me = (HttpView<Study>) HttpView.currentView();
    Study study = me.getModelBean();
%>
<p class=normal>Before setting up security for your study please read the documentation on LabKey security and
    on setting up study security.</p>
<ul>
    <li><a href="<%=new HelpTopic("security", HelpTopic.Area.SERVER).getHelpTopicLink()%>" target="_blank">LabKey Security Documentation</a></li>
    <li><a href="<%=new HelpTopic("studySecurity", HelpTopic.Area.STUDY).getHelpTopicLink()%>" target="_blank">Study Security Documentation</a></li>
</ul>

<p class=normal>All users must have READ permissions on this folder to access anything in this study. You can configure
    groups and folder security here [&nbsp;<a href="<%=new ActionURL("Security", "begin", getViewContext().getContainer())%>">Folder&nbsp;Security</a>&nbsp;].</p>

<p>If you want to set permissions on individual datasets within the study, you must enable study security.</p>

<form action="studySecurity.post" method="post">

    <input type="checkbox" name="studySecurity" <%=study.isStudySecurity() ? "checked" : ""%>>
        &nbsp;Enable Study Security<%=PageFlowUtil.helpPopup("Study Security", "Study Security must be enabled before you can configure dataset level security. When a study is first created, study security is not enabled and " +
        "folder permissions control access to the study data.")%><p/>
    <input type=image src="<%=PageFlowUtil.buttonSrc("Update")%>" value="Update">
</form>