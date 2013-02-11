<%
/*
 * Copyright (c) 2012 LabKey Corporation
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
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.query.reports.ReportsController" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        resources.add(ClientDependency.fromFilePath("study/DataViewsPanel.css"));
        resources.add(ClientDependency.fromFilePath("study/DataViewUtil.js"));
        resources.add(ClientDependency.fromFilePath("study/DataViewPropertiesPanel.js"));
        return resources;
    }
%>
<%
    JspView<ReportsController.QueryReportForm> me = (JspView<ReportsController.QueryReportForm>) HttpView.currentView();
    ReportsController.QueryReportForm bean = me.getModelBean();
%>

<table>
    <%
        for (ObjectError e : (List<ObjectError>) me.getErrors().getAllErrors())
        {
    %>      <tr><td colspan=3><font class="labkey-error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
    }
%>
</table>

<div id="queryReportForm">
</div>

<script type="text/javascript">

    function getReturnUrl()
    {
        var returnUrl = LABKEY.ActionURL.getParameter('returnUrl');
        return (undefined == returnUrl ? "" : returnUrl);
    }

    var initializeSchemaStore = function(){
        Ext4.define('LABKEY.data.Schema', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'name', type: 'string'}
            ]
        });

        var schemaStore = Ext4.create('Ext.data.Store', {
            model : 'LABKEY.data.Schema'
        });

        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'getSchemas'),
            success: function(response){
                var data = Ext4.JSON.decode(response.responseText).schemas;
                var schemas = [];

                for(var i = 0; i < data.length; i++){
                    schemas.push({name: data[i]});
                }

                schemaStore.loadRawData(schemas);
            },
            scope: this
        });

        return schemaStore;
    };

    var initializeQueryStore = function(){

        Ext4.define('LABKEY.data.Queries', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'name'},
                {name : 'description'},
                {name : 'isUserDefined', type : 'boolean'},
                {name: 'viewDataUrl'}
            ]
        });

        var config = {
            model   : 'LABKEY.data.Queries',
            proxy   : {
                type   : 'memory',
                reader : {
                    type : 'json'
                }
            }
        };

        return Ext4.create('Ext.data.Store', config);
    };

    var initializeViewStore = function(){

        Ext4.define('LABKEY.data.Views', {
            extend: 'Ext.data.Model',
            fields: [
                {name: 'name'},
                {name: 'hidden'},
                {name: 'shared'},
                {name: 'default'},
                {name: 'viewDataUrl'}
            ]
        });

        return Ext4.create('Ext.data.Store', {
            model: 'LABKEY.data.Views',
            proxy: {
                type: 'memory',
                reader: {
                    type: 'json'
                }
            }
        });
    };

    Ext4.onReady(function(){
        var queryStore = initializeQueryStore();
        var queryId = Ext4.id();
        var viewStore = initializeViewStore();
        var viewId = Ext4.id();

        queryStore.on('load', function(){
            var queryCombo = Ext4.getCmp(queryId);
            if(queryCombo){
                queryCombo.setDisabled(false);
                queryCombo.getEl().unmask();
            }
        });

        viewStore.on('load', function(){
            var viewCombo = Ext4.getCmp(viewId);

            if(viewCombo){
                viewCombo.setDisabled(false);
                viewCombo.getEl().unmask();
            }
        });

        var schemaCombo = Ext4.create('Ext.form.field.ComboBox',{
            fieldLabel: 'Schema',
            name: 'selectedSchemaName',
            store: initializeSchemaStore(),
            editable: false,
            queryMode: 'local',
            displayField: 'name',
            valueField: 'name',
            emptyText: 'None',
            listeners: {
                change: function(cmp, newValue){
                    this.schemaName = newValue;
                    var viewCombo = Ext4.getCmp(viewId);
                    var queryCombo = Ext4.getCmp(queryId);

                    if (queryCombo){
                        queryCombo.clearValue();
                        queryCombo.setDisabled(true);
                        queryCombo.getEl().mask('loading...');
                    }

                    if(viewCombo){
                        viewCombo.setDisabled(true);
                    }

                    Ext4.Ajax.request({
                        url: LABKEY.ActionURL.buildURL('query', 'getQueries'),
                        params: {
                            schemaName  : this.schemaName
                        },
                        success: function(response){
                            var data = Ext4.JSON.decode(response.responseText).queries;
                            queryStore.loadRawData(data);
                        },
                        scope: this
                    });
                },
                scope: this
            }
        });

        var queryCombo = Ext4.create('Ext.form.field.ComboBox',{
            fieldLabel: 'Query',
            id: queryId,
            name: 'selectedQueryName',
            queryMode: 'local',
            store: queryStore,
            editable: false,
            disabled: true,
            displayField: 'name',
            valueField: 'name',
            typeAhead: 'true',
            emptyText: 'None',
            listeners: {
                change: function(cmp, newValue){
                    this.queryName = newValue;
                    var viewCombo = Ext4.getCmp(viewId);

                    if(viewCombo){
                        viewCombo.clearValue();
                    }

                    if(this.queryName != null){
                        Ext4.getCmp(viewId).getEl().mask("loading...");
                        Ext4.Ajax.request({
                            url: LABKEY.ActionURL.buildURL('query', 'getQueryViews'),
                            params: {
                                schemaName: this.schemaName,
                                queryName: this.queryName
                            },
                            success: function(response){
                                var data = Ext4.JSON.decode(response.responseText).views;
                                viewStore.loadRawData(data);
                            },
                            scope: this
                        });
                    }
                },
                scope: this
            }
        });

        var viewCombo  = Ext4.create('Ext.form.field.ComboBox',{
            fieldLabel: 'View',
            name: 'view',
            id: viewId,
            name: 'selectedViewName',
            allowBlank: true,
            store: viewStore,
            queryMode: 'local',
            disabled: true,
            editable: false,
            forceSelection: false,
            displayField: 'name',
            valueField: 'name',
            typeAhead: 'true',
            emptyText: 'None',
            listeners: {
                change: function(cmp, newValue){
                    this.viewName = newValue;
                },
                scope: this
            }
        });

        var extraItems = [schemaCombo, queryCombo, viewCombo];

        extraItems.push({
            xtype: 'hiddenfield',
            name: 'srcURL',
            <%--value: '<%= bean.getSrcURL().getLocalURIString() %>'--%>
        });

        <%--extraItems.push({--%>
            <%--xtype: 'hiddenfield',--%>
            <%--name: '<%=ReportDescriptor.Prop.reportType%>',--%>
            <%--value: '<%= StudyQueryReport.TYPE%>'--%>
        <%--});--%>
        <%----%>

        var form = Ext4.create('LABKEY.study.DataViewPropertiesPanel', {
            renderTo    : 'queryReportForm',
            url : LABKEY.ActionURL.buildURL('reports', 'createQueryReport', null, {returnUrl: getReturnUrl()}),
            standardSubmit  : true,
            bodyStyle       :'background-color: transparent;',
            bodyPadding     : 10,
            border          : false,
            buttonAlign     : "left",
            width           : 575,
            fieldDefaults: {
                width : 500,
                labelWidth : 125,
                msgTarget : 'side'
            },
            visibleFields   : {
                author  : true,
                status  : true,
                modifieddate: true,
                datacutdate : true,
                category    : true,
                description : true,
                shared      : true
            },
            extraItems : extraItems,
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                style: 'background-color: transparent;',
                items: [{
                    text : 'Save',
                    handler : function(btn) {
                        var form = btn.up('form').getForm();
                        if (form.isValid())
                            form.submit();
                    },
                    scope   : this
                },{
                    text: 'Cancel',
                    handler: function(){
                        if(LABKEY.ActionURL.getParameter('returnUrl')){
                            window.location = LABKEY.ActionURL.getParameter('returnUrl');
                        } else {
                            window.location = LABKEY.ActionURL.buildURL('reports', 'manageViews');
                        }
                    }
                }]
            }]
        });
    });
</script>
