<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.authentication.opensso.OpenSSOController.*" %>
<%@ page import="org.labkey.authentication.opensso.OpenSSOController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<PickReferrerForm> me = (JspView<PickReferrerForm>)HttpView.currentView();
    PickReferrerForm bean = me.getModelBean();
%>
<form action="pickReferrer.post" method="post">
    <table>
        <tr><td colspan="2">Enter an optional URL prefix (e.g., https://www.foo.org).  If an unauthenticated user attempts to retrieve
        a protected page and the referring site starts with this prefix then the user's browser will redirect to the OpenSSO
        URL you've set.  Use this setting to cause links from a partner's site to automatically attempt authentication using OpenSSO.</td></tr>
        <tr><td colspan="2">&nbsp;</td></tr>
        <tr><td class="ms-searchform">URL prefix</td><td><input type="text" name="prefix" value="<%=h(bean.getPrefix())%>" style="width:400px;"></td></tr>
        <tr><td colspan="2">&nbsp;</td></tr>
        <tr><td colspan="2">
            <input type=image src="<%=PageFlowUtil.submitSrc()%>" value="Set">
            <%=PageFlowUtil.buttonLink("Cancel", OpenSSOController.getCurrentSettingsURL())%>
        </td></tr>
    </table><br>
</form>