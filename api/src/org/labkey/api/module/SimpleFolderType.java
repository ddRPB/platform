/*
 * Copyright (c) 2010-2012 LabKey Corporation
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
package org.labkey.api.module;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.SimpleFolderTab;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.data.xml.folderType.FolderTabDocument;
import org.labkey.data.xml.folderType.FolderType;
import org.labkey.data.xml.folderType.FolderTypeDocument;
import org.labkey.data.xml.folderType.Property;
import org.labkey.data.xml.folderType.WebPartDocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Apr 19, 2010
 * Time: 4:20:27 PM
 */
public class SimpleFolderType extends MultiPortalFolderType
{
    private static final Logger LOGGER = Logger.getLogger(SimpleFolderType.class);

    private Resource _folderTypeFile;
    private long _lastModified = 0;
    private String _name;
    private String _description;
    private List<FolderTab> _folderTabs;
    private Set<Module> _activeModules;
    private Module _defaultModule;
    public static final String FILE_EXTENSION = ".foldertype.xml";

    public SimpleFolderType(Resource folderTypeFile, FolderType folderType)
    {
        super(folderType.getName(), folderType.getDescription(), null, null, null, null);
        _folderTypeFile = folderTypeFile;
        reload();
    }

    public static SimpleFolderType create(Resource folderTypeFile)
    {
        FolderType type = parseFile(folderTypeFile);
        return new SimpleFolderType(folderTypeFile, type);
    }

    public static List<SimpleFolderType> createFromDirectory(Resource directory)
    {
        List<SimpleFolderType> folderTypes = new ArrayList<SimpleFolderType>();
        if (directory.exists() && directory.isCollection())
        {
            for (Resource file : directory.list())
            {
                if (file.isFile() && file.getName().toLowerCase().endsWith(FILE_EXTENSION))
                    folderTypes.add(create(file));
            }
        }
        return folderTypes;
    }

    private static FolderType parseFile(Resource folderTypeFile)
    {
        Logger log = Logger.getLogger(SimpleFolderType.class);
        XmlOptions xmlOptions = new XmlOptions();

        Map<String,String> namespaceMap = new HashMap<String,String>();
        namespaceMap.put("", "http://labkey.org/data/xml/folderType");
        xmlOptions.setLoadSubstituteNamespaces(namespaceMap);

        FolderTypeDocument doc;
        try
        {
            doc = FolderTypeDocument.Factory.parse(folderTypeFile.getInputStream(), xmlOptions);
        }
        catch (XmlException e)
        {
            log.error(e);
            throw new RuntimeException("Unable to load custom folder type from file " +
                    folderTypeFile.getPath() + ".", e);
        }
        catch (IOException e)
        {
            log.error(e);
            throw new RuntimeException("Unable to load custom folder type from file " +
                    folderTypeFile.getPath() + ".", e);
        }
        if(null == doc || null == doc.getFolderType())
        {
            IllegalStateException error = new IllegalStateException("Folder type definition file " +
                    folderTypeFile.getPath() + " does not contain a root 'folderType' element!");
            log.error(error);
            throw error;
        }
        return doc.getFolderType();
    }

    private List<FolderTab> createDefaultTab(FolderType type)
    {
        final String caption = type.getName() + " Dashboard";
        FolderTab tab = new FolderTab(Portal.DEFAULT_PORTAL_PAGE_ID, caption)
        {
            @Override
            public boolean isSelectedPage(ViewContext viewContext)
            {
                return true;
            }

            @Override
            public ActionURL getURL(Container container, User user)
            {
                return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(container);
            }

            @Override
            public Set<String> getLegacyNames()
            {
                return Collections.singleton(caption);
            }
        };
        return Collections.singletonList(tab);
    }

    private List<FolderTab> createFolderTabs(FolderTabDocument.FolderTab[] references)
    {
        ArrayList<FolderTab> tabs = new ArrayList<FolderTab>();
        Set<String> tabNames = new CaseInsensitiveHashSet();

        for (FolderTabDocument.FolderTab tab : references)
        {
            if (tabNames.add(tab.getName()))
            {
                FolderTab newTab = new SimpleFolderTab(tab);
                StringBuilder stringBuilder = new StringBuilder();
                if (newTab.getTabType() != FolderTab.TAB_TYPE.Container || Container.isLegalName(newTab.getName(), stringBuilder))
                {
                    tabs.add(newTab);
                }
                else
                {
                    LOGGER.error(stringBuilder);
                }
            }
            else
            {
                LOGGER.error("Folder type '" + _name + "' defines multiple tabs with the name '" + tab.getName() + "', only the first will be used.");
            }
        }

        return tabs;
    }

    public static List<Portal.WebPart> createWebParts(WebPartDocument.WebPart[] references)
    {
        List<Portal.WebPart> parts = new ArrayList<Portal.WebPart>();
        for (WebPartDocument.WebPart reference : references)
        {
            WebPartFactory factory = Portal.getPortalPart(reference.getName());
            if (factory != null)
            {
                String location = null;
                if (reference.getLocation() != null)
                    location = SimpleWebPartFactory.getInternalLocationName(reference.getLocation().toString());
                Portal.WebPart webPart = factory.createWebPart(location);
                for (Property prop : reference.getPropertyArray())
                    webPart.setProperty(prop.getName(), prop.getValue());
                parts.add(webPart);
            }
            else
                LOGGER.error("Unable to register folder type web parts: web part " + reference.getName() + " does not exist.");
        }
        return parts;
    }

    private void reload()
    {
        if (!_folderTypeFile.isFile())
        {
            // The file has been deleted.
            if (_name != null)
            {
                // Remove this folder from the master list
                ModuleLoader.getInstance().unregisterFolderType(_name);
            }
            // Don't try to reload it
            return;
        }
        FolderType type = parseFile(_folderTypeFile);
        _name = type.getName();
        _description = type.getDescription();

        if (type.isSetMenubarEnabled())
            menubarEnabled = type.getMenubarEnabled();

        if (type.getPreferredWebParts() != null)
            preferredParts = createWebParts(type.getPreferredWebParts().getWebPartArray());
        if (type.getRequiredWebParts() != null)
            requiredParts = createWebParts(type.getRequiredWebParts().getWebPartArray());

        if (type.getFolderTabs() != null)
        {
            //if folderTabs are provided, only allow other webparts if they are in the menu
            if (preferredParts != null || requiredParts != null)
            {
                boolean hasError = false;
                if (preferredParts != null)
                {
                    for (Portal.WebPart wp : preferredParts)
                    {
                        if (!wp.getLocation().equals(WebPartFactory.LOCATION_MENUBAR))
                            hasError = true;
                    }
                }
                if (requiredParts != null)
                {
                    for (Portal.WebPart wp : requiredParts)
                    {
                        if (!wp.getLocation().equals(WebPartFactory.LOCATION_MENUBAR))
                            hasError = true;
                    }
                }
                if (hasError)
                    LOGGER.error("Error in " + _folderTypeFile.getName() + ".  A folderType that contains folderTabs cannot also provide preferredWebparts or requiredWebparts with locations outside the menubar.");
            }
            _folderTabs = createFolderTabs(type.getFolderTabs().getFolderTabArray());
        }
        else
        {
            _folderTabs = createDefaultTab(type);
        }

        if (_folderTabs.size() > 0)
        {
            _defaultTab = _folderTabs.get(0);
            _folderTabs.get(0).setIsDefaultTab(true);
        }

        setWorkbookType(type.isSetWorkbookType() && type.getWorkbookType());
        setForceAssayUploadIntoWorkbooks(type.getForceAssayUploadIntoWorkbooks());
        String _iconPath = type.getFolderIconPath();
        if(_iconPath != null)
            setFolderIconPath(_iconPath);

        Set<Module> activeModules = new HashSet<Module>();
        for (String moduleName : type.getModules().getModuleNameArray())
        {
            Module module = getModule(moduleName);
            if (module == null)
                LOGGER.error("Unable to load folder type: module " + moduleName + " does not exist.");
            else
                activeModules.add(module);
        }
        _activeModules = activeModules;
        if (type.getDefaultModule() != null)
            _defaultModule = getModule(type.getDefaultModule());
        _lastModified = _folderTypeFile.getLastModified();
    }

    private void reloadIfStale()
    {
        if (_folderTypeFile.getLastModified() != _lastModified)
            reload();
    }

    @Override
    public Module getDefaultModule()
    {
        reloadIfStale();
        return _defaultModule;
    }

    @Override
    public List<Portal.WebPart> getRequiredWebParts()
    {
        reloadIfStale();
        return super.getRequiredWebParts();
    }

    @Override
    public List<Portal.WebPart> getPreferredWebParts()
    {
        reloadIfStale();
        return super.getPreferredWebParts();
    }

    @Override
    public String getName()
    {
        reloadIfStale();
        return _name;
    }

    @Override
    public String getDescription()
    {
        reloadIfStale();
        return _description;
    }

    @Override
    public boolean isWorkbookType()
    {
        reloadIfStale();
        return super.isWorkbookType();
    }

    @Override
    public Set<Module> getActiveModules()
    {
        reloadIfStale();
        return _activeModules;
    }

    @Override
    public List<FolderTab> getDefaultTabs()
    {
        return _folderTabs;
    }


    @Override
    protected String getFolderTitle(ViewContext context)
    {
        return context.getContainer().getName();
    }

    @Override
    public String toString()
    {
        return "Folder type: " + _name;
    }
}
