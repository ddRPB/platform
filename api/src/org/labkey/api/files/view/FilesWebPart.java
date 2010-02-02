/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.api.files.view;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.FileUrls;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.jsp.JspLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.*;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.*;
import org.labkey.api.webdav.WebdavService;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Mark Igra
 * Date: Jul 9, 2007
 * Time: 2:13:18 PM
 */
public class FilesWebPart extends JspView<FilesWebPart.FilesForm>
{
    private static final Logger _log = Logger.getLogger(FilesWebPart.class);

    private boolean wide = true;
    private boolean showAdmin = false;
    private String fileSet;
    private Container container;

    private static final String JSP = "/org/labkey/api/files/view/fileContent.jsp";
    private static final String JSP_RIGHT = "/org/labkey/filecontent/view/files.jsp";


    public FilesWebPart(Container c)
    {
        super(JSP);
        setModelBean(createConfig());
        container = c;
        setFileSet(null);
        setTitle("Files");
        setTitleHref(PageFlowUtil.urlProvider(FileUrls.class).urlBegin(c));
    }

    protected FilesWebPart(Container c, String fileSet)
    {
        this(c);

        if (fileSet != null)
        {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            AttachmentDirectory dir = svc.getRegisteredDirectory(c, fileSet);

            //this.fileSet = fileSet;
            getModelBean().setRoot(dir);
            getModelBean().setRootPath(c, FileContentService.FILE_SETS_LINK, fileSet);
            setTitle(fileSet);
            setTitleHref(PageFlowUtil.urlProvider(FileUrls.class).urlBegin(c).addParameter("fileSetName",fileSet));
        }
    }

    protected FilesForm createConfig()
    {
        FilesForm form = new FilesForm();

        form.setShowAddressBar(false);
        form.setShowDetails(false);
        form.setShowFolderTree(false);
        form.setRootPath(getRootContext().getContainer(), FileContentService.FILES_LINK);

        List<FilesForm.actions> actions = new ArrayList<FilesForm.actions>();

        // Navigation actions
        actions.add(FilesForm.actions.parentFolder);
        actions.add(FilesForm.actions.refresh);

        // Actions not based on the current selection
        SecurityPolicy policy = org.labkey.api.security.SecurityManager.getPolicy(getSecurableResource());
        if (policy.hasPermission(getViewContext().getUser(), InsertPermission.class))
        {
            actions.add(FilesForm.actions.upload);
            actions.add(FilesForm.actions.createDirectory);
        }

        // Actions based on the current selection
        actions.add(FilesForm.actions.download);
        if (policy.hasPermission(getViewContext().getUser(), DeletePermission.class))
        {
            actions.add(FilesForm.actions.deletePath);
        }

        if (canDisplayPipelineActions())
        {
            if (policy.hasPermission(getViewContext().getUser(), InsertPermission.class))
            {
                actions.add(FilesForm.actions.importData);
            }
        }
        if (policy.hasPermission(getViewContext().getUser(), AdminPermission.class))
            actions.add(FilesForm.actions.customize);

        form.setButtonConfig(actions.toArray(new FilesForm.actions[actions.size()]));

        return form;
    }

    public FilesWebPart(ViewContext ctx, Portal.WebPart webPartDescriptor)
    {
        this(ctx.getContainer(), StringUtils.trimToNull(webPartDescriptor.getPropertyMap().get("fileSet")));

        setWide(null == webPartDescriptor.getLocation() || HttpView.BODY.equals(webPartDescriptor.getLocation()));
        setShowAdmin(container.hasPermission(ctx.getUser(), AdminPermission.class));
        String path = webPartDescriptor.getPropertyMap().get("path");

        if (!isWide())
        {
            _path = JSP_RIGHT;
            _page = JspLoader.createPage(HttpView.currentRequest(), (String)null, _path);
        }
    }

    protected boolean canDisplayPipelineActions()
    {
        try {
            // since pipeline actions operate on the pipeline root, if the file content and pipeline roots do not
            // reference the same location, then import and customize actions should be disabled

            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            AttachmentDirectory dir = svc.getMappedAttachmentDirectory(getViewContext().getContainer(), false);
            PipeRoot root = PipelineService.get().findPipelineRoot(getViewContext().getContainer());

            if (null != root && null != dir && root.getRootPath().equals(dir.getFileSystemDirectory()))
            {
                return true;
            }
        }
        catch (MissingRootDirectoryException e)
        {
            _log.error("Error determining whether pipeline actions can be shown", e);
        }
        return false;
    }

    protected SecurableResource getSecurableResource()
    {
        return getViewContext().getContainer(); 
    }

    public boolean isWide()
    {
        return wide;
    }

    public void setWide(boolean wide)
    {
        this.wide = wide;
    }

    public boolean isShowAdmin()
    {
        return showAdmin;
    }

    public void setShowAdmin(boolean showAdmin)
    {
        this.showAdmin = showAdmin;
    }

    public String getFileSet()
    {
        return fileSet;
    }

    public void setFileSet(String fileSet)
    {
        this.fileSet = fileSet;
        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        if (null == fileSet)
        {
            try
            {
                getModelBean().setRoot(svc.getMappedAttachmentDirectory(container, false));
            }
            catch (MissingRootDirectoryException ex)
            {
                setModelBean(null);
            }
        }
        else
            getModelBean().setRoot(svc.getRegisteredDirectory(container, fileSet));
    }

    public static class Factory extends AlwaysAvailableWebPartFactory
    {
        public Factory(String location)
        {
            super("Files", location, true, false);
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            return new FilesWebPart(portalCtx, webPart);
        }

        @Override
        public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
        {
            return new CustomizeFilesWebPartView(webPart);
        }
    }

    public static class FilesForm
    {
        private AttachmentDirectory _root;
        private boolean _showAddressBar;
        private boolean _showFolderTree;
        private boolean _showDetails;
        private boolean _autoResize;
        private actions[] _buttonConfig;
        private String _rootPath;
        private Path _directory;

        public enum actions {
            download,
            deletePath,
            refresh,
            uploadTool,
            configure,
            createDirectory,
            parentFolder,
            upload,
            importData,
            customize,
        }

        public boolean isAutoResize()
        {
            return _autoResize;
        }

        public void setAutoResize(boolean autoResize)
        {
            _autoResize = autoResize;
        }

        public AttachmentDirectory getRoot()
        {
            return _root;
        }

        public void setRoot(AttachmentDirectory root)
        {
            _root = root;
        }

        public boolean isShowAddressBar()
        {
            return _showAddressBar;
        }

        public void setShowAddressBar(boolean showAddressBar)
        {
            _showAddressBar = showAddressBar;
        }

        public boolean isShowFolderTree()
        {
            return _showFolderTree;
        }

        public void setShowFolderTree(boolean showFolderTree)
        {
            _showFolderTree = showFolderTree;
        }

        public boolean isShowDetails()
        {
            return _showDetails;
        }

        public void setShowDetails(boolean showDetails)
        {
            _showDetails = showDetails;
        }

        public actions[] getButtonConfig()
        {
            return _buttonConfig;
        }

        public void setButtonConfig(actions[] buttonConfig)
        {
            _buttonConfig = buttonConfig;
        }

        public String getRootPath()
        {
            return _rootPath;
        }

        public void setRootPath(Container c, String davName)
        {
            setRootPath(c, davName, null);
        }

        public void setRootPath(Container c, String davName, String fileset)
        {
            String webdavPrefix = AppProps.getInstance().getContextPath() + "/" + WebdavService.getServletPath();
            String rootPath;

            if (davName != null)
            {
                if (fileset != null)
                    rootPath = webdavPrefix + c.getEncodedPath() + URLEncoder.encode(davName) + "/" + fileset;
                else
                    rootPath = webdavPrefix + c.getEncodedPath() + URLEncoder.encode(davName);
            }
            else
                rootPath = webdavPrefix + c.getEncodedPath();

            if (!rootPath.endsWith("/"))
                rootPath += "/";
            
            _rootPath = rootPath;
        }

        public void setDirectory(Path path)
        {
            _directory = path;
        }

        public Path getDirectory()
        {
            return _directory;
        }
    }
}
