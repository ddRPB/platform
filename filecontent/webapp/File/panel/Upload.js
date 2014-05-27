/*
 * Copyright (c) 2012-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.panel.Upload', {

    extend : 'Ext.panel.Panel',

    layout : 'fit',

    bodyPadding: 5,

    border : false,

    separator: Ext4.isWindows ? "\\" : "/",

    rootPath: "/",

    baseURL : LABKEY.contextPath + "/_webdav",

    allowFileDrop : true,

    header : false,

    bodyStyle: 'background-color:#f0f0f0;',

    constructor : function(config) {

        this.callParent([config]);

        this.addEvents('cwd', 'transferstarted', 'transfercomplete', 'closeUploadPanel');
    },

    initComponent : function() {

        this.dockedItems = this.getUploadStatusBar();
        this.items = this.getItems();

        this.callParent();
        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this, 'an operation is still pending, please wait until it is complete.');
    },

    beforeUnload : function() {
        if (this.isBusy()) {
            return 'an operation is still pending, please wait until it is complete.';
        }
    },

    isBusy : function() {
        return this.busy;
    },

    setBusy : function(busy) {
        this.busy = busy;
    },

    // From FileSystem.js
    getPrefixUrl: function() {
        var prefix = '';

        prefix = this.concatPaths(this.baseURL, this.rootPath);

        if (prefix.length > 0 && prefix.charAt(prefix.length-1) == this.separator){
            prefix = prefix.substring(0,prefix.length-1);
        }

        return prefix;
    },

    // From FileSystem.js
    concatPaths : function(a,b)
    {
        var c = 0;
        if (a.length > 0 && a.charAt(a.length-1)==this.separator) c++;
        if (b.length > 0 && b.charAt(0)==this.separator) c++;
        if (c == 0)
            return a + this.separator + b;
        else if (c == 1)
            return a + b;
        else
            return a + b.substring(1);
    },

    // From FileSystem.js
    getParentPath : function(p)
    {
        if (!p)
            p = this.rootPath;
        if (p.length > 1 && p.charAt(p.length-1) == this.separator)
            p = p.substring(0,p.length-1);
        var i = p.lastIndexOf(this.separator);
        return i == -1 ? this.rootPath : p.substring(0,i+1);
    },

    getItems : function() {
        if (this.allowFileDrop && window.Dropzone && window.Dropzone.isBrowserSupported())
        {
            this.initDropzone();
        }

        return this.getOuterPanel();
    },

    initDropzone : function () {
        var self = this;
        var dropzone = LABKEY.internal.FileDrop.registerDropzone({
            peer: function () {
                // Get the grid component from the outer Browser component
                var grid = self.ownerCt.getGrid();
                return grid ? grid.el : self.ownerCt.el;
            },

            url: 'bogus.view',
            clickable: true,
            createImageThumbnails: false,

            previewsContainer: false,

            maxFiles: 5000,
            // Allow uploads of 100GB files
            maxFilesize: 100*(1024*1024),

            // LabKey webdav only handles single POST per file
            uploadMultiple: false,

            params: {
                // Create any missing intermediate directories
                // UNDONE: Use allowDirectoryUpload configuration option here
                'createIntermediates': 'true'
            },

            accept: function (file, done) {
                var record = this.uploadPanel.getWorkingDirectory('model');
                var path = this.uploadPanel.getWorkingDirectory('path');

                if (!record || path == '/') {
                    // TODO: The browser's tree store doesn't include a model record for the root node,
                    // TODO: so just allow the upload and let the server send back an error if it fails.
                    done();
                    return;
                }

                // Check permissions before sending
                var canWrite = this.uploadPanel.fileSystem.canWrite(record);
                var canMkdir = this.uploadPanel.fileSystem.canMkdir(record);

                if (!canWrite) {
                    done("You don't have permission to write files to '" + path + "'.");
                    return;
                }

                if (file.fullPath && file.fullPath.indexOf('/') != -1 && !canMkdir) {
                    done("You don't have permission to create folders in '" + path + "'.")
                    return;
                }

                // success
                done();
            },

            init : function () {

                this.on('processing', function (file) {
                    var cwd = this.uploadPanel.getWorkingDirectory('cwd');
                    if (cwd)
                    {
                        // Overwrite if explicitly set (in confirmation by user) or if we're uploading multiple files.
                        var overwrite = file.overwrite || this.files.length > 1;

                        var uri = this.uploadPanel.fileSystem.concatPaths(cwd, file.fullPath ? file.fullPath : file.name);

                        // Save the file's uri for use in the 'transfercomplete' event
                        file.uri = this.uploadPanel.fileSystem.getURI(uri);

                        // Folder the file will be POSTed into
                        var folderUri = this.uploadPanel.fileSystem.getParentPath(file.uri)
                        this.options.url = folderUri + '?overwrite=' + (overwrite ? 'T' : 'F');
                    }
                });

                this.on('totaluploadprogress', function (progress, totalBytes, totalBytesSent) {
                    if (progress == 100 && totalBytes == 0 && totalBytesSent == 0) {
                        // Dropzone is telling us all transfers are complete
                        this.uploadPanel.hideProgressBar();
                    } else {
                        this.uploadPanel.showProgressBar();
                        this.uploadPanel.progressBar.updateProgress(progress/100);
                    }
                });

                this.on('sending', function (file, xhr, formData) {
                    if (!this.uploadPanel.isBusy()) {
                        this.uploadPanel.setBusy(true);
                        this.uploadPanel.getEl().mask("Uploading files");
                    }
                    this.uploadPanel.statusText.setText('Uploading ' + file.name + '...');
                });

                this.on('success', function (file, response, evt) {

                    // success, bail early
                    if (response === "")
                    {
                        this.uploadPanel.statusText.setText('Uploaded ' + file.name + ' successfully.');
                        return;
                    }

                    if (response && Ext4.isString(response) && response.indexOf('<status>HTTP/1.1 200 OK</status>') > -1)
                    {
//                // UNDONE: Should read status from the xml response instead of just looking for <status>
//                var xhr = evt.target;
//                var reader = new Ext4.data.reader.Xml({
//                    record : 'response',
//                    root : 'multistatus',
//                    model : 'File.data.webdav.XMLResponse'
//                });
//
//                var results = reader.read(xhr);
//                if (results.success && results.count == 1) {
//                    var record = results.records[0];
//                }

                        this.uploadPanel.statusText.setText('Uploaded ' + file.name + ' successfully.');
                        return;
                    }

                    if (response && !response.success)
                    {
                        if (response.status == 208)
                        {
                            // File exists
                            Ext4.Msg.show({
                                title : "File Conflict:",
                                msg : "There is already a file named " + file.name + ' in this location. Would you like to replace it?',
                                cls : 'data-window',
                                icon : Ext4.Msg.QUESTION,
                                buttons : Ext4.Msg.YESNO,
                                fn : function(btn) {
                                    if (btn == 'yes') {
                                        file.overwrite = true;
                                        file.status = Dropzone.ADDED;
                                        this.processFile(file);
                                    }
                                },
                                scope : this
                            });
                        }
                        else
                        {
                            file.status = Dropzone.ERROR;
                            var xhr = evt.target;
                            this.emit('error', file, response.exception, xhr);
                        }
                    }
                    else
                    {
                        this.uploadPanel.statusText.setText('Uploaded ' + file.name + ' successfully.');
                    }
                });

                this.on('error', function (file, message, xhr) {
                    this.uploadPanel.statusText.setText('Error uploading ' + file.name + (message ? (': ' + message) : ''));
                    this.uploadPanel.showErrorMsg('Error', message);
                });

                this.on('complete', function (file) {
                });

                this.on('canceled', function (file) {
                    this.uploadPanel.statusText.setText('Canceled upload of ' + file.name);
                    this.uploadPanel.setBusy(false);
                    this.uploadPanel.getEl().unmask();
                });

                this.on('queuecomplete', function () {
                    this.uploadPanel.setBusy(false);
                    this.uploadPanel.getEl().unmask();
                    this.uploadPanel.hideProgressBar();

                    var errorFiles = [];
                    var fileRecords = [];
                    for (var i = 0; i < this.files.length; i++) {
                        var file = this.files[i];
                        if (file.status == Dropzone.SUCCESS) {
                            fileRecords.push({data: {name:file.name, id:file.uri, href:file.uri}});
                        } else if (file.status == Dropzone.ERROR) {
                            errorFiles.push(file);
                        }
                    }

                    if (fileRecords.length && errorFiles.length == 0) {
                        this.uploadPanel.fireEvent('transfercomplete', {fileRecords : fileRecords});
                    }

                    this.removeAllFiles();
                });

            }
        });

        dropzone.uploadPanel = this;
    },

    getOuterPanel : function() {
        /**
         * This panel contains the radio buttons to select single/multi
         */
        var radioPanel = {
            xtype   : 'panel',
            layout  : 'form',
            width: 140,
            border : false,
            margins: '0 0 0 30',
            bodyStyle: this.bodyStyle,
            items : [{
                xtype     : 'radiogroup',
                width     : 110,
                columns   : 1,
                hideLabel : true,
                items     : [{
                    boxLabel : 'Single file',
                    name     : 'rb-file-upload-type',
                    checked  : true,
                    handler  : function(cmp, checked) {
                        if(checked){
                            uploadsPanel.getLayout().setActiveItem(this.getSingleUpload());
                        }
                    },
                    scope    : this
                },{
                    boxLabel : 'Multiple files',
                    name     : 'rb-file-upload-type',
                    handler  : function(cmp, checked) {
                        if(checked){
                            uploadsPanel.getLayout().setActiveItem(this.getMultiUpload());
                        }
                    },
                    scope    : this
                }]
            }]
        };

        /**
         * This panel contains the single/multiple upload panels
         */
        var uploadsPanel = Ext4.create('Ext.panel.Panel', {
            layout : 'card',
            deferredRender : true,
            activeItem : 0,
            border : false,
            cls : 'single-upload-panel',
            items : [this.getSingleUpload(), this.getMultiUpload()]
        });

        var uploadsContainer = Ext4.create('Ext.container.Container', {
            layout: 'hbox',
            height: 60,
            items: [radioPanel, uploadsPanel]
        });

        var outerContainer = Ext4.create('Ext.container.Container', {
            layout: 'vbox',
            height: 60,
            items: [uploadsContainer]
        });

        return [outerContainer];
    },

    getSingleUpload : function() {

        if (this.singleUpload) {
            return this.singleUpload;
        }

        var uploadId = Ext4.id();

        this.descriptionField = Ext4.create('Ext.form.field.Text', {
            name  : 'description',
            fieldLabel : 'Description',
            labelAlign : 'right',
            width : 382,
            margin: '5 0 0 0',
            disabled : true
        });

        this.singleUpload = Ext4.create('Ext.form.Panel', {
            border : false,
            frame : false,
            bodyStyle: this.bodyStyle,
            items  : [{
                xtype: 'container',
                width: 800,
                layout: 'hbox',
                items: [{
                    xtype: 'filefield',
                    name : 'file',
                    width: 452,
                    fieldLabel: 'Choose a File',
                    labelAlign: 'right',
                    buttonText: 'Browse',
                    clearOnSubmit: false, // allows form to be resubmitted in case of file overwrite
                    listeners: {
                        render: function(f) { this.fileField = f; },
                        change: function() {
                            this.descriptionField.setDisabled(false);
                            this.descriptionField.focus();
                            Ext4.getCmp(uploadId).setDisabled(false);
                        },
                        scope : this
                    }
                },{
                    xtype: 'button',
                    id: uploadId,
                    text: 'Upload',
                    cls: 'upload-button',
                    disabled: true,
                    handler: this.submitFileUploadForm,
                    scope : this
                }]
            }, this.descriptionField]
        });

        return this.singleUpload;
    },


    getMultiUpload: function() {
        if(this.multiUpload){
            return this.multiUpload;
        }

        var helpLinkHtml =  '[<a class="help-link" href="javascript:void(0);">upload help</a>]';

        var html;
        if (window.Dropzone && window.Dropzone.isBrowserSupported()) {
            html = "To upload, drag files " + (Ext4.isChrome ? "and folders " : "") +
                    "from your desktop onto this file browser.";
        } else {
            html = "Your web browser doesn't support drag and drop uploading of files.<br>" +
                    "You can upgrade your web browser or upload multiple files using an external " +
                    "<a target=_blank href='https://www.labkey.org/wiki/home/Documentation/page.view?name=webdav'>WebDAV client</a>.";
        }

        this.multiUpload = Ext4.create('Ext.panel.Panel', {
            border: false,
            bodyStyle: this.bodyStyle,
            items: [{
                xtype: 'container',
                html: html + "<p>" + helpLinkHtml
            }],
            listeners: {
                afterrender: function (container) {
                    var helpLink = container.getEl().down('a.help-link');
                    if (helpLink) {
                        helpLink.on('click', this.showHelpMessage, this);
                    }
                },
                scope: this
            }
        });

        return this.multiUpload;
    },

    getUploadStatusBar: function(){

        if (this.uploadStatusBar)
            return this.uploadStatusBar;

        this.progressBar = Ext4.create('Ext.ProgressBar', {
            width: 250,
            height: 25,
            border: false,
            autoRender : true,
            hidden: true
        });

        this.progressBarContainer = Ext4.create('Ext.container.Container', {
            width: 250,
            items: [this.progressBar]
        });

        this.statusText = Ext4.create('Ext.form.Label', {
            text: '',
            margins: '5 0 0 20',
            flex: 1,
            border: false
        });

        this.closeBtn = Ext4.create('Ext.button.Button', {
            iconCls: 'iconClose',
            tooltip: 'Close the file upload panel',
            style: 'background-color: transparent;',
            scope: this,
            border : false,
            handler: function() {
                this.fireEvent('closeUploadPanel');
            }
        });

        this.helpBtn = Ext4.create('Ext.button.Button', {
            iconCls: 'iconHelp',
            tooltip: 'File upload help',
            style: 'background-color: transparent;',
            allowDepress: false,
            scope: this,
            border : false,
            handler: this.showHelpMessage
        });

        this.uploadStatusBar = Ext4.create('Ext.panel.Panel', {
              width: 500,
              border: false,
              height: 25,
              bodyStyle: this.bodyStyle,
              layout: 'hbox',
              items: [this.progressBarContainer, this.statusText, this.helpBtn, this.closeBtn]
        });

        return this.uploadStatusBar;
    },

    showHelpMessage : function ()
    {
        var url = this.getCurrentWebdavURL();

        var msg = [
            'To upload files from your desktop to LabKey Server, drag-and-drop them onto the file area.',
            '<p>',
            'You can also use ',
            '<a target=_blank href="https://www.labkey.org/wiki/home/Documentation/page.view?name=webdav">WebDAV</a> ',
            'to transfer files to and from this folder using the Mac Finder, ' +
            'Windows Explorer or file transfer programs like <a target=_blank href="http://cyberduck.io/">CyberDuck</a>. The WebDav URL for this folder is:',
            '</p>',
            '<textarea style="font-family:monospace" readonly wrap="hard" cols="62" rows="3" size=' + url.length + '>' + Ext4.util.Format.htmlEncode(url) + '</textarea>',
            '<p>For more information on transferring files, please see the',
            '<a target="_blank" href="https://www.labkey.org/wiki/home/Documentation/page.view?name=fileUpload">file upload</a>',
            'help documentation.</p>'
        ];

        if (!Dropzone.isBrowserSupported()) {
            msg.push('<p><i><b>NOTE:</b> Your web browser does not support drag-and-drop upload.  Please consider upgrading your web browser or using a WebDAV client.</i>');
        }

        Ext4.Msg.show({
            title: "File Upload Help",
            msg: msg.join(' '),
            cls: "data-window",
            icon: Ext4.Msg.INFO,
            buttons: Ext4.Msg.OK
        });
    },

    showProgressBar : function()
    {
        if (this.progressBar)
            this.progressBar.setVisible(true);
    },

    hideProgressBar : function()
    {
        if (this.progressBar)
            this.progressBar.reset(true);
        if (this.statusText)
            this.statusText.setText('');
    },

    submitFileUploadForm : function(fb, v) {

        var cwd = this.getWorkingDirectory('cwd');

        if (cwd) {
            var form = this.singleUpload.getForm();
            var path = this.fileField.getValue();
            var i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            var name = path.substring(i+1);
            if (name.length == 0) {
                Ext4.Msg.alert('Error', 'No file selected. Please choose one or more files to upload.');
                return;
            }

            this.doPost = function(overwrite) {
                var options = {
                    method:'POST',
                    form : form,
                    url : this.fileSystem.getURI(cwd) + '?Accept=application/json&overwrite=' + (overwrite ? 'T' : 'F'),
                    name : name,
                    success : function(f, action, message) {
                        this.getEl().unmask();

                        var txt = (action.response.responseText || "").trim();
                        if (txt)
                        {
                            var response = Ext4.JSON.decode(action.response.responseText);
                            if (!response.success)
                            {
                                if (response.status == 208)
                                {
                                    Ext4.Msg.show({
                                        title : "File Conflict:",
                                        msg : "There is already a file named " + name + ' in this location. Would you like to replace it?',
                                        cls : 'data-window',
                                        icon : Ext4.Msg.QUESTION,
                                        buttons : Ext4.Msg.YESNO,
                                        fn : function(btn){
                                            if(btn == 'yes')
                                                this.doPost(true);
                                        },
                                        scope : this
                                    });
                                }
                                else
                                {
                                    this.showErrorMsg('Error', response.exception);
                                }

                                return;
                            }
                        }

                        this.singleUpload.getForm().reset();
                        this.fileField.setRawValue(null);
                        this.descriptionField.setDisabled(true);
                        this.singleUpload.down('.button[text=Upload]').setDisabled(true);

                        this.fireEvent('transfercomplete', {fileNames : [{name:name}]});
                    },
                    failure : LABKEY.Utils.displayAjaxErrorResponse,
                    scope : this
                };
                form.errorReader = new Ext4.data.reader.Xml({
                    record : 'response',
                    root : 'multistatus',
                    model : 'File.data.webdav.XMLResponse'
                });
                // set errorReader, so that handleResponse() doesn't try to eval() the XML response
                // assume that we've got a WebdavFileSystem
//                form.errorReader = this.fileSystem.transferReader;
                form.doAction(new Ext4.form.action.Submit(options));
                this.fireEvent('transferstarted');
                this.getEl().mask("Uploading " + name + '...');
            };

            this.doPost(false);
        }
    },

    changeWorkingDirectory : function(path, model, cwd) {
        this.workingDirectory = {path: path, model: model, cwd: cwd};
        this.fireEvent('cwd', model, path);
    },

    getWorkingDirectory : function(variable) {
        if (this.workingDirectory) {
            return this.workingDirectory[variable];
        }
        console.error('Upload: working directory not set.');
    },

    getCurrentWebdavURL : function () {
        var cwd = this.getWorkingDirectory('cwd');
        if (cwd)
            return this.fileSystem.getURI(cwd);
        else
            return this.fileSystem.getAbsoluteURL();
    },

    showErrorMsg : function(title, msg) {
        Ext4.Msg.show({
            title: title,
            msg: msg,
            cls : 'data-window',
            icon: Ext4.Msg.ERROR, buttons: Ext4.Msg.OK
        });
    }
});


