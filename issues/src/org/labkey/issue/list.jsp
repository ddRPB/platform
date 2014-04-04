<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.search.SearchUrls"%>
<%@ page import="org.labkey.api.security.permissions.InsertPermission"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getContainer();
    IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(c);

    if (request.getParameter("error") != null)
    {
        %><span class="labkey-error"><%=h(request.getParameter("error"))%></span><br/><%
    }
%>

<table><tr>
    <td nowrap><form name="jumpToIssue" action="<%= new ActionURL(IssuesController.JumpToIssueAction.class, c) %>" method="get">
    <%
        if (c.hasPermission(getUser(), InsertPermission.class))
        {
    %>
            <%= button("New " + names.singularName.getSource()).href(new ActionURL(IssuesController.InsertAction.class, c)) %>&nbsp;&nbsp;&nbsp;
    <%
        }
    %><input type="text" size="5" name="issueId"/>
        <%= button("Jump to " + names.singularName.getSource()).submit(true).attributes("align=\"top\" vspace=\"2\"") %></form></td>
    <td width=100%>&nbsp;</td>
    <td align="right" nowrap>
        <form action="<%=h(urlProvider(SearchUrls.class).getSearchURL(c, null))%>" method="get">
            <input type="text" size="30" name="q" value="">
            <input type="hidden" name="template" value="<%=h(IssuesController.IssueSearchResultTemplate.NAME)%>">
            <%= button("Search").submit(true).attributes("align=\"top\" vspace=\"2\"")%>
        </form>
    </td>
</tr></table>

<%
if ("true".equals(getActionURL().getParameter("navigateInPlace")))
{
%><script src="<%=getContextPath()%>/issues/hashbang.js"></script>
<script>
if (!Ext.isDefined(window.navigationStrategy))
{
    function cacheablePage(hash, el)
    {
        if (-1 == hash.indexOf("_action=list"))
            return false;
        if (!el)
            return true;
        var errors = Ext.DomQuery.jsSelect(".labkey-error", el.dom);
        var hasErrors = errors && errors.length > 0;
        return !hasErrors;
    }

    window.navigationStrategy = new LABKEY.NavigateInPlaceStrategy(
    {
        controller : "issues",
        actions :
        {
            "details": true,
            "list" : true,
            "insert" : true,
            "update" : true,
            "admin" : true,
            "emailPrefs" : true,
            "resolve" : true
        }
        , cacheable : cacheablePage
    });
}
</script><%
}
%>