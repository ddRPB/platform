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
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<AuthenticationManager.AuthLogoBean> me = (HttpView<AuthenticationManager.AuthLogoBean>) HttpView.currentView();
    AuthenticationManager.AuthLogoBean bean = me.getModelBean();
%><form action="pickAuthLogo.post" enctype="multipart/form-data" method="post">
<table >
<tr>
    <td colspan="2"><input type="hidden" name="name" value="<%=h(bean.name)%>"></td>
</tr>
<tr>
    <td class="ms-searchform">Pick a logo for the page header</td>
    <td><input type="file" name="<%=AuthenticationManager.HEADER_LOGO_PREFIX%>file" size="60">&nbsp;&nbsp;<%=bean.headerLogo%></td>
</tr>
<tr>
    <td class="ms-searchform">Pick a logo for the login page</td>
    <td><input type="file" name="<%=AuthenticationManager.LOGIN_PAGE_LOGO_PREFIX%>file" size="60">&nbsp;&nbsp;<%=bean.loginPageLogo%></td>
</tr>
<tr>
    <td class="ms-searchform">Enter a URL<%=PageFlowUtil.helpPopup("URL Instructions", "Include <code>%returnURL%</code> as the redirect parameter within the URL.  <code>%returnURL%</code> will be replaced with a link to the login page including the current page as a redirect parameter.  Examples:<br><br>http://localhost:8080/openfm/UI/Login?service=adminconsoleservice&goto=%returnURL%<br>https://machine.domain.org:8443/openfm/WSFederationServlet/metaAlias/wsfedsp?wreply=%returnURL%", true, 700)%></td>
    <td><input type="text" name="url" size="130" value="<%=h(bean.url)%>"></td>
</tr>
<tr>
    <td colspan="2">&nbsp;</td>
</tr>
<tr>
    <td colspan="2"><input type="image" src="<%=PageFlowUtil.buttonSrc("Save")%>">&nbsp;<%=PageFlowUtil.buttonLink(bean.reshow ? "Done" : "Cancel", bean.returnURL)%></td>
</tr>
</table>
</form>