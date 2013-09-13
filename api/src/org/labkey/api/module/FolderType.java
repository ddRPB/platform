/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.view.template.PageConfig;

import java.util.List;
import java.util.Set;

/**
 * User: Mark Igra
 * Date: Aug 3, 2006
 * Time: 8:49:19 PM
 *
 * Interface to define look & feel of a folder. Folders with a folder type other than FolderType.NONE will have a single
 * "tab" owned by the FolderType. This "dashboard" drill down to arbitrary URLs without changing it's look & feel.
 */
public interface FolderType
{
    public static final FolderType NONE = new CustomFolderType();

    /**
     * Configure the container with whatever active modules and web parts are required for this folder type.
     * Convention is to NOT remove web parts already in the folder.
     */
    public void configureContainer(Container c, User user, boolean brandNew);

    /**
     * This FolderType is being *removed* as the owner of the container. Clean up anything that you
     * might want. Typically this involves turning off the *permanent* bit for the web parts this
     * FolderType may have set.
     */
    public void unconfigureContainer(Container c, User user);

    /**
     * Name of this folder type. Used internally to track the folder type. Must be consistent across versions.
     * @return name
     */
    public String getName();

    @NotNull /** Old names that we should be backwards compatible with */
    public Set<String> getLegacyNames();

    /**
     * @return whether or not an admin is allowed to customize the set of tabs for this folder.
     * For folder types with only one tab, or a set backed by some other config (such as the active set of modules),
     * return false;
     */
    public boolean hasConfigurableTabs();

    /** If configurable, reset to the default set, throwing away tab configuration if it's customized. */
    public void resetDefaultTabs(Container c);

    /**
     * Description of this folder type. Used to let users know what to expect.
     */
    public String getDescription();

    /**
     * If true, rather than importing into the current container, assay upload will ask the user to create a workbook or pick an existing one and import using this container
     */
    public boolean getForceAssayUploadIntoWorkbooks();

    /**
     * Label of this folder type. This is what the user sees. Should be a short name such as "MS2" not "MS2 Folder"
     * @return User visible label
     */
    public String getLabel();

    /**
     * The filepath of the icon for this folder type, relative to the root of the webapp.
     * @return File path to the icon
     */
    @NotNull
    public String getFolderIconPath();

    /**
     * URL to start at when navigating to this folder. This is often the same as getTabURL for the portal module, or
     * getTabURL for the "owner" module, but could be any URL to an appropriate starting page.
     * @return URL for "dashboard" of this
     */
    public ActionURL getStartURL(Container c, User u);

    /**
     * Label of the start page. Typically getLabel() + " Dashboard"
     * @return Label of the start page
     */
    public String getStartPageLabel(ViewContext ctx);

    /**
     * Help topic of the start page.
     */
    public HelpTopic getHelpTopic();

    /**
     * Whether the menu bar should be shown by default.
     */
    public boolean isMenubarEnabled();

    /**
     * Module that *owns* this folder. Used in constructing navigation paths. If current URL's module is NOT part of the owning module
     * extra links will be added to automatically generated nav path
     * @return Owning module. May be null
     */
    Module getDefaultModule();

    /**
     * Return all modules required by this foldertype, INCLUDING the default module if any.  Note: in order to find the
     * requiredModules for a given container, you should call getRequiredModules() on that container rather than rely
     * on the folderType.
     * @return set
     */
    public Set<Module> getActiveModules();

    /**
     * @return all web parts that must be included in the portal page.
     */
    public List<Portal.WebPart> getRequiredWebParts();

    /**
     * @return all web parts that are recommended for inclusion in the portal page.
     */
    public List<Portal.WebPart> getPreferredWebParts();

    /**
     * Add any management links to the admin popup menu
     * @param adminNavTree popup menu
     * @param container current folder
     */
    public void addManageLinks(NavTree adminNavTree, Container container);

    @NotNull
    public AppBar getAppBar(ViewContext context, PageConfig pageConfig);

    public List<FolderTab> getDefaultTabs();

    /** @return The default tab to select, which defaults to the first (including for non-tabbed folders) */
    public FolderTab getDefaultTab();

    @Nullable
    public FolderTab findTab(String tabName);

    /** @return whether this is intended to be used exclusively for workbooks */
    public boolean isWorkbookType();

    /** @return whether this has container tabs */
    public boolean hasContainerTabs();

    /**
     * @return The pageId, which is primarily intended to support tabbed folders.  By default it will return
     * Portal.DEFAULT_PORTAL_PAGE_ID
     */
    public String getDefaultPageId(ViewContext ctx);

    /**
     * Clear active portal page if there is one
     */
    public void clearActivePortalPage();

    /**
     * @return any additional setup steps for the container creation wizard.
     */
    @NotNull
    public List<NavTree> getExtraSetupSteps(Container c);
}

