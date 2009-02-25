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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.wiki.WikiController" %>
<%@ page import="org.labkey.wiki.model.Wiki" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    WikiController.CustomizeWikiPartView me = (WikiController.CustomizeWikiPartView) HttpView.currentView();
    Portal.WebPart webPart = me.getModelBean();
    Container currentContainer = me.getCurrentContainer();

    //build array of containers/wiki lists
    String construct = "var m = new Object();\n";
    for (Map.Entry<Container, List<Wiki>> entry : me.getMapEntries().entrySet())
    {
       construct += "m[\"" + entry.getKey().getId() + "\"] = ";
       construct += "null;\n";
    }
%>
<script type="text/javascript">
LABKEY.requiresClientAPI(); //for Ext AJAX object

//output variable definition for array of containers/pages
<%=construct%>

//store current container id on client
var currentContainerId = "<%=currentContainer != null ? currentContainer.getId() : null%>";

function updatePageList()
{
    //get selection value
    var containerId = document.forms[0].webPartContainer.value;
    var wikiPageList = m[containerId];

    if(null != wikiPageList)
        loadPages(wikiPageList);
    else
    {
        //disable the submit button while we're fetching the list of pages
        disableSubmit();

        //show a "loading..." option while AJAX request is happening
        var select = document.forms[0].name;
        select.options.length = 0;
        o = new Option("loading...", "", true, true);
        select.options[select.options.length] = o;

        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL("wiki", "getPages"),
            success: onSuccess,
            failure: onError,
            method: 'GET',
            params: {'id' : containerId}
        });
    }
    return true;
}

function loadPages(wikiPageList)
{
    var select = document.forms[0].name;
    if(null == select)
        return;

    select.options.length = 0;
    var text;
    var fDefaultSelected;
    var o;

    if(wikiPageList != null)
    {
        for(var i = 0; i < wikiPageList.length; i++)
        {
            text = wikiPageList[i].name + " (" + wikiPageList[i].title + ")";
            fDefaultSelected = wikiPageList[i].name.toLowerCase() == "default";
            o = new Option(text, wikiPageList[i].name, fDefaultSelected, fDefaultSelected);
            select.options[select.options.length] = o;
        }
    }
    else
    {
        o = new Option("<no pages>", "", true, true);
        select.options[select.options.length] = o;
    }

    //re-enable the submit button while we're fetching the list of pages
    enableSubmit();
}

function enableSubmit()
{
    var btn = document.getElementById("btnSubmit");
    btn.className = "labkey-button";
    btn.href = "";
    btn.disabled = false;
}

function disableSubmit()
{
    var btn = document.getElementById("btnSubmit");
    btn.disabled = true;
    btn.className = "labkey-disabled-button";
    btn.href = "javascript:return false;";
}

function onSuccess(response, config)
{
    //parse the response text as JSON
    var json = Ext.util.JSON.decode(response.responseText);
    if(null != json)
    {
        //add the page list to the global map so that we don't need to fetch it again
        m[config.params.id] = json.pages;
        loadPages(json.pages);
    }
    else
        window.alert("Unable to parse the response from the server!");
}

function onError(response, config)
{
    if(response.status >= 500 && response.status <= 599)
    {
        //exception thrown within the server
        //parse the response text as JSON
        var json = Ext.util.JSON.decode(response.responseText);
        window.alert("The server experienced the following error: " + json.exception);
    }
    else if(response.status >= 400 && response.status <= 499)
    {
        //invalid container id
        var json = Ext.util.JSON.decode(response.responseText);
        window.alert("The server could not find the selected project or folder: " + json.exception);
    }
    else
        window.alert("Problem communicating with the server: " + response.statusText + " (" + response.status + ")");

    var select = document.forms[0].name;
    select.options.length = 0;
    o = new Option("<error getting pages>", "", true, true);
    select.options[select.options.length] = o;

}

function restoreDefaultPage()
{
    if(!currentContainerId)
        return;
    
    //set webPartContainer select value to current container
    document.forms[0].webPartContainer.value = currentContainerId;
    updatePageList();
}
</script>


<form name= "frmCustomize" method="post">
<table>
    <tr>
        <td colspan="2">
            To display a different wiki page in this web part, first select the folder containing the page
            you want to display, then select the name of the page.<br><br>
        </td>
    </tr>
    <tr>
        <td width="20%" nowrap="1">
        Folder containing the page to display:
        </td>
        <td width="80%">
        <select name="webPartContainer" onkeyup="updatePageList();" onchange="updatePageList();">
            <%
            for (Container c : me.getContainerList())
            {
                //if there's no property setting for container, select the current container.
                if (null != currentContainer && c.getId().equals(currentContainer.getId()) && webPart.getPropertyMap().get("webPartContainer") == null)
                {%>
                    <option selected value="<%=c.getId()%>"><%=h(c.getPath())%></option>
                <%}
                else
                {%>
                    <option <%=c.getId().equals(webPart.getPropertyMap().get("webPartContainer")) ? "selected" : "" %> value="<%=c.getId()%>"><%=h(c.getPath())%></option>
                <%}
            }
            %>
        </select>
        [<a href="javascript:restoreDefaultPage();">Reset to Folder Default Page</a>]
        </td>
     </tr>
    <tr>
        <td width="20%" nowrap="1">
        Name and title of the page to display:
        </td>
        <td width="80%">
        <select name="name">
            <%
            //if current container has no pages
            if (null == me.getContainerWikiList() || me.getContainerWikiList().size() == 0)
            {%>
                <option selected value="">&lt;no pages&gt;</option>
            <%}
            else
            {
                for (Wiki wikipage : me.getContainerWikiList())
                {
                    //if there's a "default" page and no other page has been selected as default, select it.
                    if (wikipage.getName().equalsIgnoreCase("default") && webPart.getPropertyMap().get("name") == null)
                    {%>
                        <option selected value="<%=h(wikipage.getName())%>"><%=h(wikipage.getName()) + " (" + h(wikipage.latestVersion().getTitle()) + ")"%></option>
                    <%}
                    else
                    {%>
                        <option <%= wikipage.getName().equals(webPart.getPropertyMap().get("name")) ? "selected" : "" %> value="<%=h(wikipage.getName())%>"><%=h(wikipage.getName()) + " (" + h(wikipage.latestVersion().getTitle()) + ")"%></option>
                    <%}
                }
            }%>
        </select>
        </td>
    </tr>
<tr>
    <td colspan="2" align="left">
        <table>
            <tr>
                <td align="left">
                    <%=PageFlowUtil.generateSubmitButton("Submit", "", "name=\"Submit\" id=\"btnSubmit\"")%>
                    <%=PageFlowUtil.generateButton("Cancel", "begin.view")%>
                </td>
            </tr>
        </table>
    </td>
</tr>
</table>
</form>
