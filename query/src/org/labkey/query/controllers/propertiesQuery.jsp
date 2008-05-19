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
<%@ page import="org.labkey.query.controllers.PropertiesForm"%>
<%@ page import="org.labkey.api.query.QueryAction"%>
<%@ page extends="org.labkey.query.controllers.Page" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<% PropertiesForm form = (PropertiesForm) __form; %>
<form method="POST" action="<%=form.urlFor(QueryAction.propertiesQuery)%>">
    <p>Name: <%=h(form.getQueryDef().getName())%></p>
    <p>Description:<br>
        <textarea name="ff_description" rows="5" cols="40"><%=h(form.ff_description)%></textarea>
    </p>
    <p>Should this query be available in child folders of this one?<br>
        <select name="ff_inheritable">
            <option value="true"<%=form.ff_inheritable ? " selected" : ""%>>Yes</option>
            <option value="false"<%=!form.ff_inheritable ? " selected" : ""%>>No</option>
        </select>
    </p>
    <p>Should this query be hidden from the user?<br>
        <select name="ff_hidden">
            <option value="true"<%=form.ff_hidden ? " selected" : ""%>>Yes</option>
            <option value="false"<%=!form.ff_hidden ? " selected" : ""%>>No</option>
        </select>
    </p>
    <labkey:button text="update" />
</form>