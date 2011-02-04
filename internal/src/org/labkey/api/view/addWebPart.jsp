<%
/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Portal.AddWebParts bean = (Portal.AddWebParts)HttpView.currentModel();
    ViewContext ctx = getViewContext();
    Container c = ctx.getContainer();
    ActionURL currentURL = ctx.getActionURL();
%>
<table width="100%">
<tr>
    <td align="left">
		<form action="<%=urlProvider(ProjectUrls.class).getAddWebPartURL(c)%>">
		<table><tr><td>
		<input type="hidden" name="pageId" value="<%=bean.pageId%>"/>
		<input type="hidden" name="location" value="<%=bean.location%>"/>
        <%=generateReturnUrlFormField(currentURL)%>
        <select name="name">
            <option value="">&lt;Select Web Part&gt;</option>
<%          for ( String name : bean.webPartNames)
            {
                %><option value="<%=h(name)%>"><%=h(name)%></option> <%
            } %>
        </select>
        </td><td>
        <%=generateSubmitButton("Add")%>
        </td></tr></table>
       </form>
    </td>
<% if (bean.rightWebPartNames != null && !bean.rightWebPartNames.isEmpty())
    { %>
    <td align="right">
        <form action="<%=urlProvider(ProjectUrls.class).getAddWebPartURL(c)%>">
        <table><tr><td>
        <input type="hidden" name="pageId" value="<%=bean.pageId%>"/>
        <input type="hidden" name="location" value="right"/>
        <%=generateReturnUrlFormField(currentURL)%>
        <select name="name">
            <option value="">&lt;Select Web Part&gt;</option>
<%          for (String name : bean.rightWebPartNames)
            {
                %><option value="<%=h(name)%>"><%=h(name)%></option> <%
            } %>
        </select>
        </td><td>
            <%=generateSubmitButton("Add")%>
        </td></tr></table>
        </form>
    </td>
<%  } %>
</tr>
</table>
