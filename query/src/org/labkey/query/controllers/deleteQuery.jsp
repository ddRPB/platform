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
<%@ page extends="org.labkey.query.controllers.Page" %>
<%@ page import="org.labkey.api.query.QueryForm"%>
<%@ page import="org.labkey.api.query.QueryAction"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<% QueryForm form = (QueryForm) __form; %>
<labkey:errors />
<form method="POST" action="<%=form.urlFor(QueryAction.deleteQuery)%>">
<p>Are you sure you want to delete the query '<%=h(form.getQueryName())%>'?</p>
<labkey:button text="OK" /> <labkey:button text="Cancel" href="<%=form.getSchema().urlFor(QueryAction.begin)%>" />
</form>