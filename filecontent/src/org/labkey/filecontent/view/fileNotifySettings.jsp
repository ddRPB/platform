<%
/*
 * Copyright (c) 2011 LabKey Corporation
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
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.filecontent.message.FileEmailConfig" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    FileEmailConfig.EmailConfigForm bean = ((JspView<FileEmailConfig.EmailConfigForm>)HttpView.currentView()).getModelBean();
    final String ID_PREFIX = "labkey_";
    String panelDiv = ID_PREFIX + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>

<div id="<%=panelDiv%>"></div>

<script type="text/javascript">
    Ext.QuickTips.init();

    Ext.onReady(function(){
        var emailDefaultCombo;
        var defaultOptionsStore = new Ext.data.Store({
            autoLoad: true,
            reader: new Ext.data.JsonReader({root:'options',id:'id'},
                [{name:'id'},{name:'label'}]
            ),
            baseParams:{isDefault:true, type:'files'},
            proxy: new Ext.data.HttpProxy({
                method: 'GET',
                url: LABKEY.ActionURL.buildURL('announcements', 'getEmailOptions')})
        });
        defaultOptionsStore.on('load', function(){emailDefaultCombo.setValue('<%=bean.getDefaultEmailOption()%>');});

        var optionsStore = new Ext.data.Store({
            reader: new Ext.data.JsonReader({root:'options',id:'id'},
                [{name:'id'},{name:'label'}]
            ),
            baseParams:{type:'files'},
            proxy: new Ext.data.HttpProxy({
                method: 'GET',
                url: LABKEY.ActionURL.buildURL('announcements', 'getEmailOptions')})
        });

        emailDefaultCombo = new Ext.form.ComboBox({
            hiddenName:'defaultFileEmailOption',
            fieldLabel:'Folder Default Setting',
            store: defaultOptionsStore,
            forceSelection:true,
            allowBlank: false,
            triggerAction:'all',
            valueField:'id',
            displayField:'label',
            width: 275,
            emptyText:'Choose a folder default...'
        });

        var idBulkEditBtn = Ext.id();
        var emailCombo = new Ext.form.ComboBox({
            hiddenName:'fileEmailOption',
            fieldLabel:'Configure Selected Users',
            store: optionsStore,
            forceSelection:true,
            allowBlank: false,
            triggerAction:'all',
            valueField:'id',
            displayField:'label',
            width: 275,
            emptyText:'Choose a notification setting...'
        });
        emailCombo.on('select', function(cmp, record, idx){
            Ext.getCmp(idBulkEditBtn).setDisabled(!cmp.isValid(true));
        });

        // buttons
        var folderDefaultBtn = new Ext.Button({text:'Update Folder Default',
            tooltip: 'Sets the default notification option for this folder',
            handler: function(b,e){
                Ext.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('filecontent', 'setDefaultEmailPref'),
                    method: 'POST',
                    timeout: 30000,
                    params: {emailPref: emailDefaultCombo.getValue()},
                    success: function(response, opts){
                        var o = eval('var $=' + response.responseText + ';$;');
                        if (o && o.success)
                        {
                            Ext.MessageBox.show({
                                title: 'Update complete',
                                animEl: folderDefaultBtn.getEl(),
                                msg: o.message
                            });
                            setTimeout(function(){Ext.MessageBox.hide()}, 1500);
                        }
                    },
                    failure: function(response, opts) {
                        LABKEY.Utils.displayAjaxErrorResponse(response, opts);
                    }
                });}
        });

        var bulkEditHandler = function(btn)
        {
            if (!emailCombo.validate())
            {
                Ext.MessageBox.alert('Error', 'Please select a notification setting from the drop down list.');
                return;
            }

            Ext.MessageBox.confirm('Update selected users',
                'Are you sure you want to update the configuration of the selected users?',
                function(btn){
                    if (btn == 'yes')
                    {
                        Ext.Ajax.request({
                            url: LABKEY.ActionURL.buildURL('announcements', 'setBulkEmailOptions'),
                            method: 'POST',
                            timeout: 30000,
                            params: {
                                type: 'files',
                                individualEmailOption: emailCombo.getValue(),
                                dataRegionSelectionKey: '<%=bean.getDataRegionSelectionKey()%>',
                                returnUrl: '<%=bean.getReturnUrl()%>'
                            },
                            success: function(response, opts){
                                var o = eval('var $=' + response.responseText + ';$;');
                                if (o && o.success)
                                    window.location = '<%=bean.getReturnUrl()%>';
                                else
                                    Ext.MessageBox.alert('Error', o.message);
                            },
                            failure: function(response, opts) {
                                LABKEY.Utils.displayAjaxErrorResponse(response, opts);
                            }
                        });
                    }
                });
        };

        var bulkEditBtn = new Ext.Button({
            disabled: true,
            id: idBulkEditBtn,
            text:'Update Settings',
            tooltip: 'Sets the notification option for all selected users',
            handler: bulkEditHandler
        });

        var formPanel = new Ext.Panel({
            renderTo: '<%=panelDiv%>',
            layout: 'form',
            labelWidth: 175,
            buttonAlign: 'left',
            labelPad: 15,
            border: false,
            bodyStyle : 'padding: 0 10px;',
            items:[emailDefaultCombo, emailCombo],
            buttons:[folderDefaultBtn, bulkEditBtn]
        });
    });
</script>



