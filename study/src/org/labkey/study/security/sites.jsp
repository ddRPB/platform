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
<%@ page import="org.labkey.study.model.Study"%>
<%@ page import="org.labkey.api.security.ACL"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.security.SecurityManager"%>
<%@ page import="org.labkey.study.model.DataSetDefinition"%>
<%@ page import="org.labkey.api.security.Group"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.ContainerManager"%>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    HttpView<Study> me = (HttpView<Study>) HttpView.currentView();
    Study study = me.getModelBean();
    User user = (User) request.getUserPrincipal();
    Container root = ContainerManager.getRoot();
%>
Per site security is NYI
