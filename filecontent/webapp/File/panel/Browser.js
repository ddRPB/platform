/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresCss("_images/icons.css");
LABKEY.requiresCss("study/DataViewsPanel.css");

Ext4.define('File.panel.Browser', {

    extend : 'Ext.panel.Panel',

    alias: ['widget.filebrowser'],

    /**
     * @cfg {Boolean} border
     */
    border : true,

    /**
     * @cfg {Boolean} bufferFiles
     */
    bufferFiles : true,

    /**
     * @cfg {Ext4.util.Format.dateRenderer} dateRenderer
     */
    dateRenderer : Ext4.util.Format.dateRenderer("Y-m-d H:i:s"),

    /**
     * @cfg {Boolean} layout
     */
    layout : 'border',

    /**
     * @cfg {Object} gridConfig
     */
    gridConfig : {},

    /**
     * @cfg {Boolean} frame
     */
    frame : false,

    /**
     * @cfg {File.system.Abstract} fileSystem
     */
    fileSystem : undefined,

    /**
     * @cfg {Number} minWidth
     */
    minWidth : 650,

    /**
     * @cfg {Boolean} showFolders
     */
    showFolderTree : true,

    /**
     * @cfg {Boolean} expandFolderTree
     */
    expandFolderTree : true,

    /**
     * @cfg {Boolean} expandUpload
     */
    expandUpload : false,

    /**
     * @cfg {Boolean} isWebDav
     */
    isWebDav : false,

    /**
     * @cfg {Boolean} rootName
     */
    rootName : LABKEY.serverName || "LabKey Server",

    /**
     * @cfg {String} rootOffset
     */
    rootOffset : '',

    /**
     * @cfg {Boolean} showAddressBar
     */
    showAddressBar : true,

    /**
     * @cfg {Boolean} showDetails
     */
    showDetails : true,

    /**
     * @cfg {Boolean} showProperties
     */
    showProperties : false,

    /**
     * @cfg {Boolean} showUpload
     */
    showUpload : true,

    /**
     * @cfg {Array} tbarItems
     */
    tbarItems : [],

    actionsConfig : [],

    actionGroups: {},

    adminUser : false,

    actionsUpToDate : false,

    // provides a default color for backgrounds if they are shown
    bodyStyle : 'background-color: lightgray;',

    statics : {
        /**
         * Requests the set of actions available for the given containerPath
         * @private
         */
        _getActions : function(cb, containerPath, scope)
        {
            Ext4.Ajax.request({
                url: LABKEY.ActionURL.buildURL('pipeline', 'actions', containerPath, { allActions:true }),
                method: 'GET',
                disableCaching: false,
                success : Ext4.isFunction(cb) ? cb : undefined,
                failure: LABKEY.Utils.displayAjaxErrorResponse,
                scope: scope
            });
        },

        /**
         * This requests the pipeline action configuration from pipeline/getPipelineActionConfig.
         * NOTE: The shape of the returned configuration is as follows:
         * {
         *      expandFileUpload: {boolean}
         *      fileConfig: {string}
         *      gridConfig: {Object}
         *      importDataEnabled: {boolean}
         *      inheritedFileConfig: {boolean/object}
         *      inheritedTbarConfig: {boolean/object}
         *      tbarActions: {Array} - An Array of objects specifying tbar actions currently
         *                              available via the users configuration. NOTE: This is
         *                              not the permissions to show these actions.
         * }
         */
        _getPipelineConfiguration : function(cb, containerPath, scope)
        {
            var cacheResult = File.panel.Browser._pipelineConfigurationCache[containerPath];

            if (Ext4.isObject(cacheResult)) {
                // cache hit
                Ext4.isFunction(cb) ? cb.call(scope, cacheResult) : undefined
            }
            else if (Ext4.isArray(cacheResult)) {
                // cache miss -- inflight
                File.panel.Browser._pipelineConfigurationCache[containerPath].push({fn: cb, scope: scope});
            }
            else {
                // prep cache
                File.panel.Browser._pipelineConfigurationCache[containerPath] = [{fn: cb, scope: scope}];

                // cache miss
                Ext4.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('pipeline', 'getPipelineActionConfig', containerPath),
                    method: 'GET',
                    disableCaching: false,
                    success : function(response) {

                        var callbacks = File.panel.Browser._pipelineConfigurationCache[containerPath];
                        File.panel.Browser._pipelineConfigurationCache[containerPath] = response;

                        for (var c=0; c < callbacks.length; c++) {
                            Ext4.isFunction(callbacks[c].fn) ? callbacks[c].fn.call(callbacks[c].fn.scope || this, response) : undefined
                        }
                    },
                    failure: LABKEY.Utils.displayAjaxErrorResponse,
                    scope: scope
                });
            }
        },

        _pipelineConfigurationCache : {}
    },

    constructor : function(config) {

        Ext4.QuickTips.init();

        // Clone the config so we don't modify the original config object
        config = Ext4.Object.merge({}, config);
        this.setFolderOffset(config.fileSystem.getOffsetURL());

        this.callParent([config]);
    },

    initComponent : function() {

        fb = this;
        var testFlag = document.createElement("div");
        testFlag.id = 'testFlag';

        document.body.appendChild(testFlag);

        this.createActions();
        this.items = this.getItems();
        this.importDataEnabled = true;
        this.initializeToolbar();

        // Attach listeners
        this.on('folderchange', this.onFolderChange, this);
        Ext4.Ajax.timeout = 60000;
        this.callParent();
    },

    createActions : function() {
        this.actions = {};

        this.actions.parentFolder = new Ext4.Action({
            text: 'Parent Folder',
            itemId: 'parentFolder',
            tooltip: 'Navigate to parent folder',
            iconCls:'iconUp',
            disabledClass:'x-button-disabled',
            handler : this.onTreeUp,
            scope: this,
            hideText: true
        });

        this.actions.refresh = new Ext4.Action({
            text: 'Refresh',
            itemId: 'refresh',
            tooltip: 'Refresh the contents of the current folder',
            iconCls: 'iconReload',
            disabledClass: 'x-button-disabled',
            handler : this.onRefresh,
            scope: this,
            hideText: true
        });

        this.actions.createDirectory = new Ext4.Action({
            text: 'Create Folder',
            itemId: 'createDirectory',
            iconCls:'iconFolderNew',
            tooltip: 'Create a new folder on the server',
            disabledClass: 'x-button-disabled',
            handler : this.onCreateDirectory,
            scope: this,
            hideText: true
        });

        this.actions.download = new Ext4.Action({
            text: 'Download',
            itemId: 'download',
            tooltip: 'Download the selected files or folders',
            iconCls: 'iconDownload',
            disabledClass: 'x-button-disabled',
            disabled: true,
            handler: this.onDownload,
            scope: this,
            hideText: true
        });
        
        this.actions.deletePath = new Ext4.Action({
            text: 'Delete',
            itemId: 'deletePath',
            tooltip: 'Delete the selected files or folders',
            iconCls: 'iconDelete',
            disabledClass: 'x-button-disabled',
            handler: this.onDelete,
            scope: this,
            disabled: true,
            hideText: true
        });

        this.actions.renamePath = new Ext4.Action({
            text: 'Rename',
            itemId: 'renamePath',
            tooltip: 'Rename the selected file or folder',
            iconCls: 'iconRename',
            disabledClass: 'x-button-disabled',
            handler : this.onRename,
            scope: this,
            disabled: true,
            hideText: true
        });

        this.actions.movePath = new Ext4.Action({
            text: 'Move',
            itemId: 'movePath',
            tooltip: 'Move the selected file or folder',
            iconCls: 'iconMove',
            disabledClass: 'x-button-disabled',
            handler : this.onMoveClick,
            scope: this,
            disabled: true,
            hideText: true
        });

        this.actions.help = new Ext4.Action({
            text: 'Help',
            itemId: 'help',
            scope: this
        });

        this.actions.showHistory = new Ext4.Action({
            text: 'Show History',
            itemId: 'showHistory',
            scope: this
        });

        this.actions.uploadTool = new Ext4.Action({
            text: 'Multi-file Upload',
            itemId: 'uploadTool',
            iconCls: 'iconUpload',
            tooltip: "Upload multiple files or folders using drag-and-drop<br>(requires Java)",
            disabled: true,
            scope: this
        });

        this.actions.upload = new Ext4.Action({
            text: 'Upload Files',
            itemId: 'upload',
            enableToggle: true,
            pressed: this.showUpload && this.expandUpload,
            iconCls: 'iconUpload',
            handler : this.onUpload,
            scope: this,
            disabledClass:'x-button-disabled',
            tooltip: 'Upload files or folders from your local machine to the server'
        });

        this.actions.appletFileAction = new Ext4.Action({
            text: '&nbsp;&nbsp;&nbsp;&nbsp;Choose File&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;',
            itemId: 'appletFileAction',
            scope: this,
            disabled: false,
            cls: 'applet-button'
        });

        this.actions.appletDirAction = new Ext4.Action({
            text: '&nbsp;Choose Folder&nbsp;',
            itemId: 'appletDirAction',
            scope: this,
            disabled: false,
            cls: 'applet-button'
        });

        this.actions.appletDragAndDropAction = new Ext4.Action({
            text: 'Drag and Drop&nbsp;',
            itemId: 'appletDragAndDropAction',
            scope: this,
            disabled: false,
            cls     : 'applet-button'
        });

        this.actions.folderTreeToggle = new Ext4.Action({
            text: 'Toggle Folder Tree',
            itemId: 'folderTreeToggle',
            enableToggle: true,
            iconCls: 'iconFolderTree',
            disabledClass:'x-button-disabled',
            tooltip: 'Show or hide the folder tree',
            hideText: true,
            handler : function() { this.tree.toggleCollapse(); },
            scope: this
        });

        this.actions.importData = new Ext4.Action({
            text: 'Import Data',
            itemId: 'importData',
            handler: this.onImportData,
            iconCls: 'iconDBCommit',
            disabledClass:'x-button-disabled',
            tooltip: 'Import data from files into the database, or analyze data files',
            scope: this
        });

        this.actions.customize = new Ext4.Action({
            text: 'Admin',
            itemId: 'customize',
            iconCls: 'iconConfigure',
            disabledClass:'x-button-disabled',
            tooltip: 'Configure the buttons shown on the toolbar',
            handler: this.showAdminWindow,
            scope: this
        });

        this.actions.editFileProps = new Ext4.Action({
            text: 'Edit Properties',
            itemId: 'editFileProps',
            iconCls: 'iconEditFileProps',
            disabledClass:'x-button-disabled',
            tooltip: 'Edit properties on the selected file(s)',
            handler : function() { Ext4.Msg.alert('Edit File Properties', 'This feature is not yet implemented.'); },
            disabled : true,
            hideText: true,
            scope: this
        });

        this.actions.emailPreferences = new Ext4.Action({
            text: 'Email Preferences',
            itemId: 'emailPreferences',
            iconCls: 'iconEmailSettings',
            disabledClass:'x-button-disabled',
            tooltip: 'Configure email notifications on file actions.',
            hideText: true,
            handler : this.onEmailPreferences,
            scope: this
        });

        this.actions.auditLog = new Ext4.Action({
            text: 'Audit History',
            itemId: 'auditLog',
            iconCls: 'iconAuditLog',
            disabledClass:'x-button-disabled',
            tooltip: 'View the files audit log for this folder.',
            handler : function() {
                window.location = LABKEY.ActionURL.buildURL('filecontent', 'showFilesHistory', this.containerPath);
            },
            scope: this
        });
    },

    initializeActions : function() {
        var action, a;
        for (a in this.actions) {
            if (this.actions.hasOwnProperty(a)) {
                action = this.actions[a];
                if (action && action.isAction) {
                    action.initialConfig.prevText = action.initialConfig.text;
                    action.initialConfig.prevIconCls = action.initialConfig.iconCls;

                    if (action.initialConfig.hideText) {
                        action.setText(undefined);
                    }

                    if (action.initialConfig.hideIcon) {
                        action.setIconClass(undefined);
                    }
                }
            }
        }
    },

    initializeToolbar : function() {
        if (this.isWebDav) {
            this.tbar = this.getWebDavToolbarItems();
        }
        else {
            this.configureActions();
        }
    },

    configureActions : function() {
        var configure = function(response) {
            var json = Ext4.JSON.decode(response.responseText);
            if (json.config) {
                Ext4.defer(this.updateActions, 250, this);

                // First intialize all the actions prepping them to be shown
                this.initializeActions();

                // Configure the actions on the toolbar based on whether they should be shown
                this.configureTbarActions({tbarActions: json.config.tbarActions, actions: json.config.actions});
            }
        };
        File.panel.Browser._getPipelineConfiguration(configure, this.containerPath, this);
    },

    configureTbarActions : function(config) {
        var tbarConfig = config.tbarActions;
        var actionButtons = config.actions;
        var toolbar = this.getDockedItems()[0];
        var buttons = [], i;

        if (toolbar) {
            // Remove the current toolbar incase we just customized it.
            this.removeDocked(toolbar);
        }

        // Use as a lookup
        var mapTbarItems = {};

        if (this.tbarItems) {
            for (i=0; i < this.tbarItems.length; i++) {
                mapTbarItems[this.tbarItems[i]] = true;
            }
        }

        if (tbarConfig) {
            var action, actionConfig;

            // Iterate across tbarConfig as button ordering is determined by array order
            for (i=0; i < tbarConfig.length; i++) {

                // check map to ensure that we should process this action
                if (mapTbarItems[tbarConfig[i].id]) {
                    actionConfig = tbarConfig[i];
                    action = this.actions[actionConfig.id];

                    // TODO: Why special processing?
                    if (actionConfig.id == 'customize') {
                        action.setDisabled(this.disableGeneralAdminSettings);
                    }

                    buttons.push(action);
                }
            }
        }
        else if (this.tbarItems) {

            for (i=0; i < this.tbarItems.length; i++) {
                action = this.actions[this.tbarItems[i]];

                // TODO: Why special processing?
                if (this.tbarItems[i] == 'customize') {
                    action.setDisabled(this.disableGeneralAdminSettings);
                }

                buttons.push(action);
            }
        }

        if (actionButtons) {
            for (i=0; i < actionButtons.length; i++) {
                if (actionButtons[i].links[0].display === 'toolbar') {
                    var action = new Ext4.Action({
                        id : actionButtons[i].links[0].id,
                        itemId : actionButtons[i].links[0].id,
                        text : actionButtons[i].links[0].label,
                        handler: this.executeToolbarAction,
                        scope : this,
                        disabled : true
                    });
                    buttons.push(action);
                }
            }
        }

        this.addDocked({xtype: 'toolbar', dock: 'top', items: buttons, enableOverflow : true});
    },

    executeToolbarAction : function(item, e)
    {
        var action = this.actionMap[item.itemId];

        if (action)
            this.executeImportAction(action);
    },

    initGridColumns : function() {

        var columns = [{
            xtype : 'templatecolumn',
            text  : '',
            dataIndex : 'icon',
            sortable : true,
            width : 25,
            height : 20,
            tpl : '<img height="16px" width="16px" src="{icon}" alt="{type}">',
            scope : this
        },{
            xtype : 'templatecolumn',
            text  : 'Name',
            dataIndex : 'name',
            sortable : true,
            height : 20,
            minWidth : 200,
            flex : 3,
            tpl : '<div height="16px" width="100%">' +
                        '<div style="float: left;"></div>' +
                        '<div style="padding-left: 20px; white-space:normal !important;">' +
                            '<span style="display: inline-block;">{name:htmlEncode}</span>' +
                        '</div>' +
                   '</div>',
            scope : this
        },
        {header: "Last Modified",  flex: 1, dataIndex: 'lastmodified', sortable: true,  hidden: false, height : 20, renderer: this.dateRenderer},
        {header: "Size",           flex: 1, dataIndex: 'size',         sortable: true,  hidden: false, height : 20, renderer:Ext4.util.Format.fileSize, align : 'right'},
        {header: "Created By",     flex: 1, dataIndex: 'createdby',    sortable: true,  hidden: false, height : 20, renderer:Ext4.util.Format.htmlEncode},
        {header: "Description",    flex: 1, dataIndex: 'description',  sortable: true,  hidden: false, height : 20, renderer:Ext4.util.Format.htmlEncode},
        {header: "Usages",         flex: 1, dataIndex: 'actionHref',   sortable: true,  hidden: false, height : 20},// renderer:LABKEY.FileSystem.Util.renderUsage},
        {header: "Download Link",  flex: 1, dataIndex: 'fileLink',     sortable: true,  hidden: true, height : 20},
        {header: "File Extension", flex: 1, dataIndex: 'fileExt',      sortable: true,  hidden: true, height : 20, renderer:Ext4.util.Format.htmlEncode}
        ];

        var success = function(response) {
            var json = Ext4.JSON.decode(response.responseText);
            if (json && json.config && json.config.gridConfig) {
                json = json.config.gridConfig.columns;
                var finalColumns = [], i= 1, g = this.getGrid();
                for (; i < json.length; i++) {
                    columns[json[i].id-1].hidden = json[i].hidden;
                    finalColumns[i-1] = columns[json[i].id-1];
                }
                g.reconfigure(g.getStore(), finalColumns);
            }
        };

        File.panel.Browser._getPipelineConfiguration(success, this.containerPath, this);

        return columns;
    },

    getItems : function() {
        var items = [this.getGridCfg()];

        if (this.showFolderTree) {
            items.push(this.getFolderTreeCfg());
        }

        if (this.showUpload) {
            items.push(this.getUploadPanel());
        }

        if (this.showDetails) {
            items.push(this.getDetailPanel());
        }

        return items;
    },

    getFileStore : function() {

        if (this.fileStore) {
            return this.fileStore;
        }

        var storeConfig = {
            model : this.fileSystem.getModel(),
            autoLoad : true,
            proxy : this.fileSystem.getProxyCfg(this.getRootURL())
        };

        if (this.bufferFiles) {
            Ext4.apply(storeConfig, {
                remoteSort : true,
                buffered : true,
                leadingBufferZone : 300,
                pageSize : 200,
                purgePageCount : 0
            });
        }

        Ext4.apply(storeConfig.proxy.extraParams, {
            paging : this.bufferFiles
        });

        this.fileStore = Ext4.create('Ext.data.Store', storeConfig);

        this.on('gridchange', this.updateGridProxy, this);
        this.on('treechange', this.updateGridProxy, this);

        // 'load' only works on non-buffered stores
        this.fileStore.on(this.bufferFiles ? 'prefetch' : 'load', function() {
            this.gridMask.cancel();
            if (this.grid) {
                this.getGrid().getEl().unmask();
            }
        }, this);

        return this.fileStore;
    },

    updateGridProxy : function(url) {
        if (!this.gridTask) {
            this.gridTask = new Ext4.util.DelayedTask(function() {
                this.fileStore.getProxy().url = this.gridURL;
                this.gridMask.delay(250);
                this.fileStore.load();
            }, this);
        }
        this.gridURL = url;
        this.gridTask.delay(50);
    },

    getRootURL : function() {
        return this.fileSystem.concatPaths(LABKEY.ActionURL.getBaseURL(), this.fileSystem.getBaseURL().replace(LABKEY.contextPath, ''));
    },

    getFolderURL : function() {
        return this.fileSystem.concatPaths(this.getRootURL(), this.getFolderOffset());
    },

    getFolderOffset : function() {
        return this.rootOffset;
    },

    setFolderOffset : function(offsetPath, model) {

        var path = offsetPath;

        if (model && Ext4.isString(offsetPath)) {
            var splitUrl = offsetPath.split(this.getRootURL());
            if (splitUrl && splitUrl.length > 1) {
                path = splitUrl[1];
            }
        }

        this.rootOffset = path;
        this.currentFolder = model;
        this.fireEvent('folderchange', path, model);
    },

    getFolderTreeCfg : function() {

        var store = Ext4.create('Ext.data.TreeStore', {
            model : this.fileSystem.getModel('xml'),
            proxy : this.fileSystem.getProxyCfg(this.getRootURL(), 'xml'),
            root : {
                text : this.fileSystem.rootName,
                id : '/',
                expanded : true,
                icon : LABKEY.contextPath + '/_images/labkey.png'
            }
        });

        // Request Root Node Information
        Ext4.Ajax.request({
            url    : this.getRootURL(),
            headers: store.getProxy().headers,
            method : 'GET',
            params : store.getProxy().getPropParams({action: 'read'}) + '&depth=0',
            success: function(response) {
                if (response && response.responseXML) {
                    var records = store.getProxy().getReader().readRecords(response.responseXML).records;
                    if (Ext4.isArray(records)) {
                        var data = records[0].data;
                        Ext4.apply(store.tree.root.data, {
                            options : data.options,
                            uri     : data.uri
                        });
                        this.setFolderOffset(store.tree.root.data.id, store.tree.root);
                        return;
                    }
                }
                console.warn('Failed to initialize root. See Browser.getFolderTreeCfg');
            },
            scope : this
        });

        if (!this.showHidden) {
            store.on('beforeappend', function(s, node) {
                if (node && node.data && Ext4.isString(node.data.name)) {
                    if (node.data.name.indexOf('.') == 0) {
                        return false;
                    }
                }
            }, this);
        }

        store.on('load', function() {
//            var p = this.getFolderOffset();
//            if (p && p[p.length-1] != '/')
//                p += '/';
            this.ensureVisible(this.fileSystem.getOffsetURL());
            this.onRefresh();
        }, this, {single: true});

        this.on('gridchange', this.expandPath, this);

        return {
            xtype : 'treepanel',
            itemId : 'treenav',
            region : 'west',
            cls : 'themed-panel',
            flex : 1,
            store : store,
            collapsed: !this.expandFolderTree,
            collapsible : true,
            collapseMode : 'mini',
            split : true,
            useArrows : true,
            listeners : {
                beforerender : function(t) {
                    this.tree = t;
                },
                select : this.onTreeSelect,
                scope : this
            }
        };
    },

    ensureVisible : function(id) {

        if (!this.vStack) {
            this.vStack = [];
        }
        var node = this.tree.getView().getTreeStore().getRootNode().findChild('id', id, true);
        if (!node) {
            var p = this.fileSystem.getParentPath(id);
            if (p == '/') {
                return;
            }
            if (id.length > 0 && id.substring(id.length-1) != '/') {
                id += '/';
            }
            this.vStack.push(id);
            this.ensureVisible(p);
        }
        else {
            if (!node.isLeaf()) {
                var s = this.vStack.pop();
                var fn = s ? function() { this.ensureVisible(s);  } : undefined;
                if (!s) {
                    this.setFolderOffset(node.data.id, node);
                    this.tree.getSelectionModel().select(node, false, false);
                }
                node.expand(false, fn, this);
            }
        }
    },

    expandPath : function(p) {
        var path = this.getFolderOffset();
        var idx = this.tree.getView().getStore().find('id', path);
        if (idx) {
            var rec = this.tree.getView().getStore().getAt(idx);
            if (rec) {
                this.tree.getView().expand(rec);
                this.tree.getSelectionModel().select(rec);
                return;
            }
        }
        console.warn('Unable to expand path: ' + path);
    },

    onTreeSelect : function(selModel, rec) {
        if (rec.isRoot()) {
            this.setFolderOffset(rec.data.id, rec);
            this.fireEvent('treechange', this.getFolderURL());
        }
        else if (rec.data.uri && rec.data.uri.length > 0) {
            this.setFolderOffset(rec.data.uri, rec);
            this.fireEvent('treechange', this.getFolderURL());
        }
        this.tree.getView().expand(rec);
    },

    getGrid : function() {

        if (this.grid) {
            return this.grid;
        }

        return this.getGridCfg();
    },

    /**
     * updateActions will request the set of actions available from both the server and the pipeline
     */
    updateActions : function()
    {
        if (this.isPipelineRoot)
        {
            var actionsReady = false;
            var pipelineReady = false;

            var actions;
            var me = this;

            var check = function() {
                if (actionsReady && pipelineReady)
                    me.updatePipelineActions(actions);
            };

            var actionCb = function(response) {
                var o = Ext4.decode(response.responseText);
                actions = o.success ? o.actions : [];
                actionsReady = true;
                check();
            };

            var pipeCb = function(response) {
                me.configureActionConfigs(response);
                pipelineReady = true;
                check();
            };

            File.panel.Browser._getActions(actionCb, this.containerPath, this);
            File.panel.Browser._getPipelineConfiguration(pipeCb, this.containerPath, this);
        }
    },

    // worst named method ever
    configureActionConfigs : function(response) {
        var resp = Ext4.decode(response.responseText);

        if (resp.config.actions)
        {
            this.importDataEnabled = resp.config.importDataEnabled ? resp.config.importDataEnabled : false;
            var actionConfigs = resp.config.actions;

            for (var i=0; i < actionConfigs.length; i++)
            {
                this.actionsConfig[actionConfigs[i].id] = actionConfigs[i];
            }
        }

        this.actions.importData[(!this.importDataEnabled && !this.adminUser ? 'hide' : 'show')]();
    },

    updatePipelineActions : function(actions)
    {
        this.pipelineActions = [];
        this.actionMap = {};
        this.actionGroups = {};
        this.fileMap = {};

        if (actions && actions.length && (this.importDataEnabled || this.adminUser))
        {
            var pipelineActions = this.parseActions(actions), pa;
            for (var i=0; i < pipelineActions.length; i++)
            {
                if (!pipelineActions[i].link)
                    continue;

                pa = pipelineActions[i];

                var config = this.actionsConfig[pa.groupId];
                if (pa.link.href)
                {
                    pa.enabled = config ? (config.links[0].display === 'enabled') : true;
                }

                this.pipelineActions.push(pa);
                this.actionMap[pa.id] = pa;

                if (pa.groupId in this.actionGroups)
                    this.actionGroups[pa.groupId].actions.push(pa);
                else
                    this.actionGroups[pa.groupId] = {label: pa.groupLabel, actions: [pa]};

                // Populate this.fileMap
                for (var f=0; f < pa.files.length; f++)
                {
                    if (!this.fileMap[pa.files[f]])
                        this.fileMap[pa.files[f]] = {};
                    this.fileMap[pa.files[f]][pa.groupId] = 1;
                }
            }
        }

        this.updateActionButtons();

        this.actionsUpToDate = true;
    },

    //TODO: Button logic should work for multiple file selection (MFS)
    updateActionButtons : function()
    {
        for(var key in this.actionMap)
        {
            if(!this.selectedRecord)
                break;

            if(Ext4.getCmp(key))
            {
                var disabled = true;
                for(var i = 0; i < this.actionMap[key].files.length; i++)
                {

                    if(this.selectedRecord.data.name === this.actionMap[key].files[i])
                    {
                        disabled = false;
                        break;
                    }
                }
                Ext4.getCmp(key).setDisabled(disabled);
            }
        }
    },

    isValidFileCheck : function(files, filename)
    {
        for(var i = 0; i < files.length; i++)
        {
            if(files[i] === filename)
            {
                return true;
            }
        }
        return false;
    },

    onImportData : function()
    {
//        if (this.importDataEnabled) {
//
//            if (!this.importDataWindow) {
//                this.importDataWindow = Ext4.create('File.panel.Import', {
//                    actionGroups: this.actionGroups,
//                    selection: this.getSelection(),
//                    autoRequestImport: true
//                });
//            }
//            else {
//                this.importDataWindow.requestImport({
//                    actionGroups: this.actionGroups,
//                    selection: this.getSelection()
//                });
//            }
//        }
        var actionMap = [],
                actions = [],
                items   = [],
                alreadyChecked = false, // Whether we've already found an enabled action to make the default selection
                hasAdmin = false, pa, shrink, shortMessage, longMessage;

        //TODO make this work for multiple file selection (MFS)
        for (var ag in this.actionGroups)
        {
            if (!this.selectedRecord)
            {
                console.warning('No record selected for data import');
                break;
            }

            var group = this.actionGroups[ag];
            pa = group.actions[0];

            var bad = 0, badFiles = [];
            var potential = false;

            for(var i=0; i < group.actions.length; i++){
                if(group.actions[i].files.length <= 0)
                {
                    bad++;
                }
                else if(!this.isValidFileCheck(group.actions[i].files, this.selectedRecord.data.name))
                {
                    badFiles.push(group.actions[i].files);
                }
            }
            if(bad == group.actions.length)
                continue;
            else if(bad + badFiles.length == group.actions.length)
            {
                potential = true;
            }

            badFiles['fileset'] = {};
            for(var i = 0; i < badFiles.length; i++)
            {
                for(var j = 0; j < badFiles[i].length; j++)
                {
                    badFiles['fileset'][badFiles[i][j]] = true;
                }
            }

            if(!potential)
            {
                shortMessage = group.label + '<br>' + '<span style="margin-left:5px;" class="labkey-mv"> using ' + '1' + ' of ' + pa.files.length + ' files</span>';
                longMessage = 'This action will use the selected file: <br>' + this.selectedRecord.data.name;
            }
            else
            {
                shortMessage = group.label + '<br>' + '<span style="margin-left:5px;" class="labkey-mv">None of the selected files can be used</span>';
                longMessage = 'The following files could be used for this action: <br>'
                for(var key in badFiles['fileset'])
                {
                    longMessage += key + '<br>';
                }
            }

            var radioGroup = Ext4.create('Ext.form.RadioGroup', {
                fieldLabel : shortMessage,
                labelWidth : 250,
                itemCls    : 'x-check-group',
                columns    : 1,
                labelSeparator: '',
                items      : [],
                scope : this,
                tooltip : longMessage,
                listeners : {
                    render : function(rg){
                        rg.setHeight(rg.getHeight()+10);
                        !potential ? this.setFormFieldTooltip(rg, 'info.png') : this.setFormFieldTooltip(rg, 'warning-icon-alt.png');
                    },
                    scope : this
                }
            });

            var radios = [];

            for (var i=0; i < group.actions.length; i++)
            {
                var action = group.actions[i];
                if (action.link.href && (action.enabled || this.adminUser))
                {
                    var label = action.link.text;

                    // administrators always see all actions
                    if (!action.enabled && this.adminUser)
                    {
                        label = label.concat(' <span class="labkey-error">*</span>');
                        hasAdmin = true;
                    }

                    actionMap[action.id] = action;
                    radios.push({
                        xtype: 'radio',
                        checked: action.enabled && !alreadyChecked,
                        labelSeparator: '',
                        boxLabel: label,
                        name: 'importAction',
                        inputValue: action.id,
                        disabled : potential
                    });

                    if (action.enabled)
                    {
                        alreadyChecked = true;
                    }
                }
            }
            actions.push({
                xtype: 'radiogroup',
                fieldLabel : shortMessage,
                labelWidth : 275,
                showAsWarning: potential,
                border: '0 0 1 0',
                style: 'border-bottom: 1px dashed lightgray',
                columns    : 1,
                labelSeparator: '',
                items: radios,
                scope : this,
                tooltip : longMessage,
                listeners : {
                    render : function(rg) {
                        rg.setHeight(rg.getHeight()+10);
                        this.setFormFieldTooltip(rg, (rg.showAsWarning ? 'warning-icon-alt.png' : 'info.png'));
                    },
                    scope : this
                }
            });
        }

        var actionPanel = Ext4.create('Ext.form.FormPanel', {
            bodyStyle   : 'padding:10px;',
            labelWidth  : 250,
            defaultType : 'radio',
            items       : actions
        });
        items.push(actionPanel);

        if (hasAdmin)
        {
            items.push({
                html      : 'Actions marked with an asterisk <span class="labkey-error">*</span> are only visible to Administrators.',
                bodyStyle : 'padding:10px;',
                border    : false
            });
        }

        if (!this.importDataEnabled  && !this.adminUser)
        {
            items.push({
                html      : 'This dialog has been disabled from the admin panel and is only visible to Administrators.',
                bodyStyle : 'padding:10px;',
                border    : false
            });
            shrink = true;
        }
        else if (!this.selectedRecord)
        {
            items.push({
                html      : 'No files selected to process.',
                bodyStyle : 'padding:10px;',
                border    : false
            });
            shrink = true;
        }
        else if (!radioGroup)
        {
            items.push({
                html      : 'There are no actions capable of processing ' + this.selectedRecord.data.name,
                bodyStyle : 'padding:10px;',
                border    : false
            });
            shrink = true;
        }
        var buttons = [];
        if(!shrink)
        {
            buttons = [{
                text: 'Import',
                id: 'btn_submit',
                listeners: {click:function(button, event) {
                    this.submitForm(actionPanel, actionMap);
                    win.close();
                }, scope:this}
            },{
                text: 'Cancel',
                id: 'btn_cancel',
                handler: function(){win.close();}
            }];
        }
        else
        {
            buttons = [{
                text: 'Cancel',
                id: 'btn_cancel',
                handler: function(){win.close();}
            }];
        }


        var win = Ext4.create('Ext.Window', {
            title: 'Import Data',
            width: shrink ? 300 : 725,
            height: shrink ? 150 : undefined,
            autoShow: true,
            autoScroll: true,
            modal: true,
            items: items,
            buttons: buttons
        });
    },

    parseActions : function(actions) {

        var pipelineActions = [];
        if (actions && actions.length)
        {
            for (var i=0; i < actions.length; i++)
            {
                var action = actions[i];
                var config = {
                    files: action.files,
                    groupId: action.links.id,
                    groupLabel: action.links.text,
                    multiSelect: action.multiSelect,
                    emptySelect: action.emptySelect,
                    description: action.description
                };

                // only a single target for the action (no submenus)
                if (!action.links.items && action.links.text && action.links.href)
                {
                    config.id = action.links.id;
                    config.link = {text: action.links.text, id: action.links.id, href: action.links.href};

                    pipelineActions.push(config);
                }
                else
                {
                    for (var j=0; j < action.links.items.length; j++)
                    {
                        var item = action.links.items[j];

                        config.id = item.id;
                        config.link = item;

                        pipelineActions.push(config);
                    }
                }
            }
        }
        return pipelineActions;
    },

    submitForm : function(panel, actionMap)
    {
        // client side validation
        var selection = panel.getForm().getValues();
        var action = actionMap[selection.importAction];

        if ('object' == typeof action)
            this.executeImportAction(action);
    },

    executeImportAction : function(action)
    {
        if (action)
        {
            //TODO convert for multiple file selection (MFS)
            var selections = [this.selectedRecord], i;
            var link = action.link;

            // if there are no selections, treat as if all are selected
            if (selections.length == 0)
            {
                selections = [];
                var store = this.grid.getStore();

                for (i=0; i <store.getCount(); i++)
                {
                    selections.push(store.getAt(i));
                }
            }

            if (link && link.href)
            {
                if (selections.length == 0)
                {
                    Ext4.Msg.alert("Execute Action", "There are no files selected");
                    return false;
                }

                var form = document.createElement("form");
                form.setAttribute("method", "post");
                form.setAttribute("action", link.href);

                for (i=0; i < selections.length; i++)
                {
                    var files = action.files;
                    for (var j = 0; j < files.length; j++)
                    {
                        if (files[j] == selections[i].data.name)
                        {
                            var fileField = document.createElement("input");
                            fileField.setAttribute("name", "file");
                            fileField.setAttribute("value", selections[i].data.name);
                            form.appendChild(fileField);
                            break;
                        }
                    }
                }
                document.body.appendChild(form);    // Not entirely sure if this is necessary
                form.submit();
            }
        }
    },

    onSelection : function(g, selectedRecords) {
        this.changeTestFlag(false, false);

        if (this.showDetails) {
            if (selectedRecords.length == 1)
                this.getDetailPanel().update(selectedRecords[0].data);
            else
                this.getDetailPanel().update('');
        }

        var tb = this.getDockedComponent(0); // button toolbar
        if (tb) {
            if (this.actions.download) {
                this.actions.download.setDisabled(!this.fileSystem.canRead(selectedRecords[0])); // TODO: multi-select
            }
            if (this.actions.renamePath) {
                if (selectedRecords.length > 1)
                    this.actions.renamePath.setDisabled(true);
                else if (selectedRecords.length == 1)
                    this.actions.renamePath.setDisabled(false);
                else
                    this.actions.renamePath.setDisabled(true);
            }
            if (this.actions.movePath) {
                if (selectedRecords.length > 1)
                    this.actions.movePath.setDisabled(true);
                else if (selectedRecords.length == 1)
                    this.actions.movePath.setDisabled(false);
                else
                    this.actions.movePath.setDisabled(true);
            }
            if (this.actions.deletePath) {
                if (selectedRecords.length > 1)
                    this.actions.deletePath.setDisabled(true);
                else if (selectedRecords.length == 1)
                    this.actions.deletePath.setDisabled(false);
                else
                    this.actions.deletePath.setDisabled(true);
            }
        }

        this.selectedRecord = selectedRecords[0]; // TODO: Fix multi-select
        this.changeTestFlag(true, false);
        this.updateActionButtons();
        this.changeTestFlag(true, true);
    },

    getGridCfg : function() {
        var config = Ext4.Object.merge({}, this.gridConfig);

        // Optional Configurations
        Ext4.applyIf(config, {
            flex : 4,
            region : 'center',
            border: false,
            style : 'border-top: 1px solid #b4b4b4;',
            viewConfig : {
                emptyText : '<span style="margin-left: 5px; opacity: 0.3;"><i>No Files Found</i></span>'
            }
        });

        if (!config.selModel && !config.selType) {
            config.selModel = {
                selType: 'checkboxmodel',
                mode: 'MULTI',
                pruneRemoved: false // designated 'private' on Ext.selection.Model in Ext 4.1.0
            };
        }

        Ext4.apply(config, {
            xtype   : 'grid',
            store   : this.getFileStore(),
            columns : this.initGridColumns(),
            listeners : {
                beforerender : function(g) { this.grid = g; },
                selectionchange : this.onSelection,
                itemdblclick : function(g, rec) {
                    if (rec && rec.data && rec.data.collection) {
                        this.setFolderOffset(rec.data.id, rec);
                        this.fireEvent('gridchange', this.getFolderURL());
                    }
//                    else {
//                        // Download the file
//                        this.onDownload({recs : [rec]});
//                    }
                },
                scope : this
            }
        });

        this.gridMask = new Ext4.util.DelayedTask(function() {
            this.getGrid().getEl().mask('Loading...');
        }, this);

        return config;
    },

    getWebDavToolbarItems : function(){
        var baseItems = [];

        this.actions.folderTreeToggle.setText('');
        this.actions.parentFolder.setText('');
        this.actions.refresh.setText('');
        this.actions.createDirectory.setText('');
        this.actions.download.setText('');
        this.actions.deletePath.setText('');

        this.actions.createDirectory.setDisabled(true);
        this.actions.download.setDisabled(true);
        this.actions.deletePath.setDisabled(true);
        this.actions.upload.setDisabled(true);

        if (this.showFolderTree) {
            baseItems.push(this.actions.folderTreeToggle);
        }

        baseItems.push(
                this.actions.parentFolder,
                this.actions.refresh,
                this.actions.createDirectory,
                this.actions.download,
                this.actions.deletePath,
                this.actions.upload
        );

        if (Ext4.isArray(this.tbarItems)) {
            for (var i=0; i < this.tbarItems.length; i++) {
                baseItems.push(this.tbarItems[i]);
            }
        }

        return baseItems;
    },

    onFolderChange : function(path, model) {
        var tb = this.getDockedComponent(0), action;
        if (tb) {
            action = tb.getComponent('deletePath');
            if (action)
                action.setDisabled(!this.fileSystem.canDelete(model)); // TODO: Check grid selection

            action = tb.getComponent('createDirectory');
            if (action)
                action.setDisabled(!this.fileSystem.canMkdir(model));

            action = tb.getComponent('upload');
            if (action)
                action.setDisabled(!this.fileSystem.canWrite(model));

            if (this.actions.download) {
                this.actions.download.setDisabled(!this.fileSystem.canRead(model));
            }
            if (this.actions.renamePath) {
                this.actions.renamePath.setDisabled(true);
            }
            if (this.actions.movePath) {
                this.actions.movePath.setDisabled(true);
            }
            if (this.actions.deletePath) {
                this.actions.deletePath.setDisabled(true);
            }
        }
        this.currentDirectory = model;
    },

    getUploadPanel : function() {
        if (this.uploadPanel) {
            return this.uploadPanel;
        }

        this.uploadPanel = Ext4.create('File.panel.Upload', {
            region : 'north',
            header : false,
            hidden : !this.expandUpload,
            fileSystem : this.fileSystem,
            listeners : {
                transfercomplete : function() {
                    this.getFileStore().load();
                },
                scope : this
            }
        });

        // link upload panel to know when directory changes
        this.on('folderchange', this.uploadPanel.changeWorkingDirectory, this.uploadPanel);

        return this.uploadPanel;
    },

    getDetailPanel : function() {
        if (this.details)
            return this.details;

        var detailsTpl = new Ext4.XTemplate(
           '<table class="fb-details">' +
                '<tr><th>Name:</th><td>{name}</td></tr>' +
                '<tr><th>WebDav URL:</th><td><a target="_blank" href="{href}">{href}</a></td></tr>' +
                '<tpl if="lastmodified != undefined">' +
                    '<tr><th>Modified:</th><td>{lastmodified:this.renderDate}</td></tr>' +
                '</tpl>' +
                '<tpl if="createdby != undefined && createdby.length">' +
                    '<tr><th>Created By:</th><td>{createdby}</td></tr>' +
                '</tpl>' +
                '<tpl if="size != undefined && size">' +
                    '<tr><th>Size:</th><td>{size:this.renderSize}</td></tr>' +
                '</tpl>' +
           '</table>',
        {
            renderDate : function(d) {
                return this.dateRenderer(d);
            },
            renderSize : function(d) {
                return this.sizeRenderer(d);
            }
        }, {dateRenderer : this.dateRenderer, sizeRenderer : Ext4.util.Format.fileSize});

        this.details = Ext4.create('Ext.Panel', {
            region : 'south',
            flex : 1,
            maxHeight : 100,
            tpl : detailsTpl
        });

        this.on('folderchange', function(){ this.details.update(''); }, this);

        return this.details;
    },

    onCreateDirectory : function() {

        var onCreateDir = function(panel) {

            var path = this.getFolderURL();
            if (panel.getForm().isValid()) {
                var values = panel.getForm().getValues();
                if (values && values.folderName) {
                    var folder = values.folderName;
                    this.fileSystem.createDirectory({
                        path : path + folder,
                        success : function(path) {
                            win.close();

                            // Reload stores
                            this.getFileStore().load();
                            var nodes = this.tree.getSelectionModel().getSelection();
                            if (nodes && nodes.length)
                                this.tree.getStore().load({node: nodes[0]});
                        },
                        failure : function(response) {
                            win.close();
                            Ext4.Msg.alert('Create Directory', 'Failed to create directory. This directory may already exist.');
                        },
                        scope : this
                    });
                }
            }

        };

        var win = Ext4.create('Ext.Window', {
            title : 'Create Folder',
            width : 300,
            height: 150,
            modal : true,
            autoShow : true,
            items : [{
                xtype: 'form',
                itemId: 'foldernameform',
                border: false, frame : false,
                items : [{
                    xtype : 'textfield',
                    name : 'folderName',
                    itemId : 'foldernamefield',
                    flex : 1,
                    allowBlank : false,
                    emptyText : 'Folder Name',
                    width : 250,
                    margin : '32 10 0 10',
                    validateOnBlur : false,
                    validateOnChange : false,
                    validator : function(folder) {
                        if (folder && folder.length) {
                            console.log('would validate if folder is already present.');
                        }
                        return true;
                    },
                    listeners : {
                        afterrender : function(field) {
                            var map = new Ext4.util.KeyMap({
                                target: field.el,
                                binding: [{
                                    key   : Ext4.EventObject.ENTER,
                                    fn    : function() { onCreateDir.call(this, field.up('form')); },
                                    scope : this
                                },{
                                    key   : Ext4.EventObject.ESC,
                                    fn    : function() { win.close(); },
                                    scope : this
                                }]
                            });
                        },
                        scope : this
                    },
                    scope : this
                }]
            }],
            cls : 'data-window',
            defaultFocus : 'foldernamefield',
            buttons : [
                {text : 'Submit', handler : function(b) {
                    onCreateDir.call(this, b.up('window').getComponent('foldernameform'));
                }, scope: this},
                {text : 'Cancel', handler : function() { win.close(); }, scope: this}
            ]
        });
    },

    onDelete : function() {

        var recs = this.getGrid().getSelectionModel().getSelection();

        if (recs && recs.length > 0) {

            Ext4.Msg.show({
                title : 'Delete Files',
                cls : 'data-window',
                msg : 'Are you sure that you want to delete the ' + (recs[0].data.collection ? 'folder' : 'file') +' \'' + recs[0].data.name + '\'?',
                buttons : Ext4.Msg.YESNO,
                icon : Ext4.Msg.QUESTION,
                fn : function(btn) {
                    if (btn == 'yes') {
                        this.fileSystem.deletePath({
                            path : recs[0].data.href,
                            success : function(path) {
                                this.getFileStore().load();
                            },
                            failure : function(response) {
                                 Ext4.Msg.alert('Delete', 'Failed to delete.');
                            },
                            scope : this
                        });
                    }
                },
                scope : this
            });

        }
    },

    // TODO: Support multiple selection download -- migrate to file system (MFS)
    onDownload : function(config) {

        var recs = (config && config.recs) ? config.recs : this.getGrid().getSelectionModel().getSelection();

        if (recs.length == 1) {
            this.fileSystem.downloadResource({record: recs[0]});
        }
        else {
            Ext4.Msg.show({
                title : 'File Download',
                msg : 'Please select a file or folder on the right to download.',
                buttons : Ext4.Msg.OK
            });
        }
    },

    onEmailPreferences : function() {
        Ext4.create('File.panel.EmailProps', { containerPath: this.containerPath }).show();
    },

    _moveOnCallback : function(fs, src, dest, rec) {
        this.onRefresh();
        var tb = this.getDockedItems()[0];
        if (tb) {
            tb.getComponent('renamePath').setDisabled(true);
            tb.getComponent('movePath').setDisabled(true);
        }
    },

    onMoveClick : function() {
        if (!this.currentDirectory || !this.selectedRecord)
            return;

        //TODO Make this not a workaround for a single file (MFS)
        var selections = [this.selectedRecord];

//        function validateNode(record, node){
//            if(record.data.options.indexOf('MOVE') == -1){
//                node.disabled = true;
//            }
//
//            var path;
//            Ext4.each(selections, function(rec){
//                path = rec.get('path');
//                if(this.fileSystem.isChild(node.id, path) || node.id == path || node.id == this.fileSystem.getParentPath(rec.get('path'))){
//                    node.disabled = true;
//                    return false;
//                }
//            }, this);
//        }


        var treePanel = Ext4.create('Ext.tree.Panel', {
            itemId          : 'treepanel',
            height          : 200,
            root            : this.tree.getRootNode(),
            rootVisible     : true,
            autoScroll      : true,
            animate         : true,
            enableDD        : false,
            containerScroll : true,
            collapsible     : false,
            collapseMode    : 'mini',
            collapsed       : false,
            cmargins        :'0 0 0 0',
            border          : true,
            stateful        : false,
            pathSeparator   : ';'
        });
        treePanel.getRootNode().expand();

        var okHandler = function(win) {
            var panel = treePanel;
            var node = panel.getSelectionModel().getLastSelected();
            if (!node) {
                Ext4.Msg.alert('Move Error', 'Must pick a destination folder');
                return;
            }

            if (node.data.id == this.currentDirectory.data.id) {
                Ext4.Msg.alert('Move Error', 'Cannot move a file to the folder it is already in');
                return;
            }

            var destination = node.data.id;
            if (destination != '/') {
                // TODO: Doesn't handle @cloud ?
                destination = destination.split('@files')[1]; // Worrisome
            }

            var toMove = [];
            for (var i=0; i < win.fileRecords.length; i++) {
                toMove.push({record : win.fileRecords[i]});
            }

            this.doMove(toMove, destination, function() { win.close(); }, this);
        };

        var win = Ext4.create('Ext.window.Window', {
            title: "Choose Destination",
            modal: true,
            autoShow : true,
            cls: 'data-window',
            width: 270,
            closeAction: 'hide',
            origName: name,
            fileRecords: selections,
            draggable : false,
            items: [{
                bodyStyle: 'padding: 10px;',
                border : false, frame : false,
                items: [{
                    border: false,
                    html: 'Choose target location for ' + selections.length + ' files:'
                },
                    treePanel
                ]
            }],
            buttons: [{
                text: 'Move',
                scope: this,
                handler: function(btn){
                    var win = btn.findParentByType('window');
                    okHandler.call(this, win);
                }
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.findParentByType('window').hide();
                }
            }]
        });
    },

    //TODO Various validation tasks should be preformed on this.
    doMove : function(toMove, destination, callback, scope) {

        for (var i = 0; i < toMove.length; i++){
            var selected = toMove[i];

            var newPath = this.fileSystem.concatPaths(destination, (selected.newName || selected.record.data.name));

            //used to close the ext window
            if(callback)
                callback.call(this);

            this.fileSystem.movePath({
                fileRecord : selected,
                // TODO: Doesn't handle @cloud.  Shouldn't the fileSystem know this?
                source: selected.record.data.id.split('@files')[1],
                destination: newPath,
                isFile: !this.selectedRecord.data.collection,
                success: function(fs, source, destination, response){
                    this._moveOnCallback(fs, source, destination, selected.record);
                },
                failure: LABKEY.Utils.displayAjaxErrorResponse,
                scope: this
            });
        }
    },

    onRename : function() {

        if (!this.selectedRecord || !this.currentDirectory) {
            return;
        }

        var me = this;
        var okHandler = function() {
            var field = Ext4.getCmp('renameText');
            var newName = field.getValue();

            if (!newName || !field.isValid()) {
                alert('Must enter a valid filename');
            }

            if (newName == win.origName) {
                win.close();
                return;
            }

            var destination = me.currentDirectory.data.id;
            if (destination != '/') {
                // TODO: Doesn't handle @cloud
                destination = destination.split('@files')[1];
            }

            me.doMove([{
                record: win.fileRecord,
                newName: newName
            }], destination, function(){
                win.close();
            }, me);
        };

        var name = this.selectedRecord.data.name;

        var win = Ext4.create('Ext.window.Window', {
            title: "Rename",
            width: 280,
            autoHeight: true,
            modal: true,
            cls : 'data-window',
            closeAction: 'destroy',
            origName: name,
            fileRecord: this.selectedRecord,
            draggable : false,
            autoShow : true,
            items: [{
                xtype: 'form',
                labelAlign: 'top',
                bodyStyle: 'padding: 10px;',
                border : false, frame : false,
                items: [{
                    xtype: 'textfield',
                    id : 'renameText',
                    allowBlank: false,
                    regex: /^[^@\/\\;:?<>*|"^][^\/\\;:?<>*|"^]*$/,
                    regexText: "Folder must be a legal filename and not start with '@' or contain one of '/', '\\', ';', ':', '?', '<', '>', '*', '|', '\"', or '^'",
                    width: 250,
                    labelAlign: 'top',
                    itemId: 'nameField',
                    fieldLabel: 'Filename',
                    labelSeparator : '',
                    value: name,
                    listeners: {
                        afterrender: function(cmp) {
                            cmp.focus(false, 100);
                        }
                    }
                }]
            }],
            buttons: [{
                text: 'Rename',
                handler: function(btn) {
                    var win = btn.findParentByType('window');
                    okHandler.call(this, win);
                },
                scope: this
            },{
                text: 'Cancel',
                handler: function(btn) {
                    btn.findParentByType('window').close();
                }
            }],
            keys: [{
                key: Ext4.EventObject.ENTER,
                handler: okHandler,
                scope: this
            }]
        });

    },

    onRefresh : function() {
        this.gridMask.delay(0);
        if (!this.refreshTask) {
            this.refreshTask = new Ext4.util.DelayedTask(function() {
                this.getFileStore().load();
            }, this);
        }
        this.refreshTask.delay(250);
        this.selectedRecord = undefined;
    },

    onTreeUp : function() {
        var tree = this.getComponent('treenav');
        if (tree && tree.getSelectionModel().hasSelection()) {
            var sm = tree.getSelectionModel();
            var node = sm.getSelection()[0];
            if (node && node.parentNode) {
                sm.select(node.parentNode);
            }
        }
    },

    onUpload : function() {
        var up = this.getUploadPanel();
        up.isVisible() ? up.hide() : up.show();
    },

    getAdminPanelCfg : function(pipelineFileProperties) {
        return {
            xtype : 'fileadmin',
            width : 750,
            height: 562,
            plain : true,
            border: false,
            pipelineFileProperties: pipelineFileProperties.config,
            fileProperties : pipelineFileProperties.fileProperties,
            isPipelineRoot : this.isPipelineRoot,
            containerPath : this.containerPath,
            listeners: {
                success: function(gridUpdated)
                {
                    this.configureActions();
                    if (gridUpdated) {
                        this.initGridColumns();
                    }
                },
                close: function() { this.adminWindow.close(); },
                scope: this
            }
        };
    },

    showAdminWindow: function() {
        if (this.adminWindow && !this.adminWindow.isDestroyed) {
            this.adminWindow.setVisible(true);
        }
        else {
            Ext4.Ajax.request({
                scope: this,
                url: LABKEY.ActionURL.buildURL('pipeline', 'getPipelineActionConfig', this.containerPath),
                success: function(response){
                    var json = Ext4.JSON.decode(response.responseText);
                    this.adminWindow = Ext4.create('Ext.window.Window', {
                        cls: 'data-window',
                        title: 'Manage File Browser Configuration',
                        closeAction: 'destroy',
                        layout: 'fit',
                        modal: true,
                        items: [this.getAdminPanelCfg(json)]
                    }).show();
                },
                failure: function(){}
            });
        }
    },

    setFormFieldTooltip : function(component, icon)
    {
        var label = component.getEl().down('label');
        if (label) {
            var helpImage = label.createChild({
                tag: 'img',
                src: LABKEY.contextPath + '/_images/' + icon,
                style: 'margin-bottom: 0px; margin-left: 8px; padding: 0px;',
                width: 12,
                height: 12
            });
            Ext4.QuickTips.register({
                target: helpImage,
                text: component.tooltip,
                title: ''
            });
        }
    },

    changeTestFlag : function(folderMove, importReady)
    {
        var flag = document.getElementById('testFlag');
        var appliedClass = "";
        if(folderMove)
        {
            appliedClass += 'test-grid-ready';
            if(importReady)
            {
                appliedClass += ' test-import-ready';
            }
        }
        else if(importReady)
        {
            appliedClass = 'test-import-ready';
        }

        flag.setAttribute('class', appliedClass);
    }
});
