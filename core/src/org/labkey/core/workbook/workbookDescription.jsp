<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%
    JspView me = (JspView) HttpView.currentView();
    Container container = me.getViewContext().getContainer();
%>
<script type="text/javascript">
    LABKEY.requiresCss("editInPlaceElement.css");
    LABKEY.requiresScript("editInPlaceElement.js");
</script>

<style type="text/css">
    .wb-name
    {
        font-size: 12pt;
        font-weight: bold;
    }
    .wb-title
    {
        font-size: 12pt;
        font-weight: bold;

    }
    .wb-name-title-container
    {
        padding-bottom: 5px;
    }
</style>

<div class="wb-name-title-container"><span id="wb=name" class="wb-name"><%=PageFlowUtil.filter(container.getName() + ":")%></span>
    <span id="wb-title" class="wb-title"><%=PageFlowUtil.filter(container.getTitle())%></span></div>

<div id="wb-description"><%=PageFlowUtil.filter(container.getDescription())%></div>

<script type="text/javascript">
    var _descriptionEditor;

    Ext.onReady(function(){
        if (!LABKEY.Security.currentUser.canUpdate)
            return;

        _descriptionEditor = new LABKEY.ext.EditInPlaceElement({
            applyTo: 'wb-description',
            multiLine: true,
            emptyText: 'No description provided. Click to add one.',
            updateHandler: onUpdateDescription
        });

        new LABKEY.ext.EditInPlaceElement({
            applyTo: 'wb-title',
            updateHandler: onUpdateTitle
        });
    });

    function onUpdateDescription(value, oldValue, successCallback, failureCallback, scope) {
        var params = {description: value};
        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL("core", "updateDescription"),
            method: "POST",
            success: function() {
                successCallback.call(scope, value, oldValue);
            },
            failure: function() {
                Ext.Msg.alert("Error", "Error updating description!");
                failureCallback.call(scope, value, oldValue);
            },
            jsonData: params,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

    function onUpdateTitle(value, oldValue, successCallback, failureCallback, scope) {
        var params = {title: value};
        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL("core", "updateTitle"),
            method: "POST",
            success: function() {
                successCallback.call(scope, value, oldValue);
            },
            failure: function() {
                Ext.Msg.alert("Error", "Error updating title!");
                failureCallback.call(scope, value, oldValue);
            },
            jsonData: params,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

</script>