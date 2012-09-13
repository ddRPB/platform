/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.view.template.PageConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: Nov 9, 2011
 */
public abstract class MultiPortalFolderType extends DefaultFolderType
{
    private String _activePortalPage = null;
    protected FolderTab _defaultTab;

    public MultiPortalFolderType(String name, String description, @Nullable List<Portal.WebPart> requiredParts, @Nullable List<Portal.WebPart> preferredParts, Set<Module> activeModules, Module defaultModule)
    {
        super(name, description, requiredParts, preferredParts, activeModules, defaultModule);
    }

    @Override
    public boolean hasConfigurableTabs()
    {
        return true;
    }

    private Collection<Portal.WebPart> getTabs(Container container)
    {
        List<Portal.WebPart> tabs = Portal.getParts(container, FolderTab.FOLDER_TAB_PAGE_ID);

        // Build up a list of all the currently defined tab names
        Set<String> currentTabNames = new HashSet<String>();
        for (FolderTab folderTab : getDefaultTabs())
        {
            currentTabNames.add(folderTab.getName());
        }

        // Filter out ones that we've saved that are no longer part of the folder type
        List<Portal.WebPart> filtered = new ArrayList<Portal.WebPart>(tabs.size());
        for (Portal.WebPart tab : tabs)
        {
            if (currentTabNames.contains(tab.getName()))
            {
                filtered.add(tab);
            }
        }

        // If we don't have any matching tabs any more, reset to the default set
        if (filtered.isEmpty())
            filtered = resetDefaultTabs(container);

        return filtered;
    }

    @Override @NotNull
    public AppBar getAppBar(ViewContext ctx, PageConfig pageConfig)
    {
        Collection<Portal.WebPart> tabs = getTabs(ctx.getContainer());

        List<NavTree> buttons = new ArrayList<NavTree>();

        _activePortalPage = null;
        Map<String, NavTree> navMap = new LinkedHashMap<String, NavTree>();
        Map<String, Portal.WebPart> tabMap = new HashMap<String, Portal.WebPart>();
        for (Portal.WebPart tab : tabs)
        {
            FolderTab folderTab = findTab(tab.getName());
            tabMap.put(tab.getName(), tab);
            if (folderTab != null && folderTab.isVisible(ctx.getContainer(), ctx.getUser()))
            {
                String label = folderTab.getCaption(ctx);
                NavTree nav = new NavTree(label, folderTab.getURL(ctx.getContainer(), ctx.getUser()));
                buttons.add(nav);
                navMap.put(tab.getName(), nav);
                // Stop looking for a tab to select if we've already found one
                if (_activePortalPage == null && folderTab.isSelectedPage(ctx))
                {
                    nav.setSelected(true);
                    _activePortalPage = folderTab.getName();
                }
            }
        }
        // If we didn't find a match, and there is a tab that should be the default, and we're on the generic portal page
        if (_activePortalPage == null && !navMap.isEmpty() && ctx.getActionURL().equals(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(ctx.getContainer())))
        {
            Map.Entry<String, NavTree> entry = navMap.entrySet().iterator().next();
            // Mark the first tab as selected
            _activePortalPage = entry.getKey();
            entry.getValue().setSelected(true);
        }

        migrateLegacyPortalPage(ctx.getContainer());

        return new AppBar(getFolderTitle(ctx), ctx.getContainer().getStartURL(ctx.getUser()), buttons);
    }

    @Override
    public ActionURL getStartURL(Container c, User user)
    {
        Collection<Portal.WebPart> tabs = getTabs(c);
        for (Portal.WebPart tab : tabs)
        {
            FolderTab folderTab = findTab(tab.getName());
            if (folderTab.isVisible(c, user))
            {
                return folderTab.getURL(c, user);
            }
        }
        return super.getStartURL(c, user);
    }

    private void migrateLegacyPortalPage(Container container)
    {
        List<Portal.WebPart> legacyPortalParts = new ArrayList<Portal.WebPart>(Portal.getParts(container));
        if (!legacyPortalParts.isEmpty())
        {
            // Check if there's a tab that has the legacy portal page ID
            for (FolderTab folderTab : getDefaultTabs())
            {
                if (Portal.DEFAULT_PORTAL_PAGE_ID.equalsIgnoreCase(folderTab.getName()))
                {
                    // If so, we don't need to migrate anything
                    return;
                }
            }

            String defaultTabName = getDefaultTab().getName();
            List<Portal.WebPart> mergedParts = new ArrayList<Portal.WebPart>(Portal.getParts(container, defaultTabName));
            Iterator<Portal.WebPart> i = legacyPortalParts.iterator();
            boolean changed = false;
            while (i.hasNext())
            {
                Portal.WebPart defaultPortalPart = i.next();
                if (!WebPartFactory.LOCATION_MENUBAR.equals(defaultPortalPart.getLocation()))
                {
                    // Add it to the default tab if it's not already there
                    if (!mergedParts.contains(defaultPortalPart))
                    {
                        defaultPortalPart.setPageId(defaultTabName);
                        mergedParts.add(defaultPortalPart);
                    }
                    // Remove it from the legacy portal page
                    i.remove();
                    changed = true;
                }
            }

            if (changed)
            {
                // Save the legacy page and the newly merged page
                Portal.saveParts(container, legacyPortalParts);
                Portal.saveParts(container, defaultTabName, mergedParts);
            }
        }
    }

    protected abstract String getFolderTitle(ViewContext context);

    @Override
    public String getDefaultPageId(ViewContext ctx)
    {
        String result;
        if (_activePortalPage != null)
        {
            // If we have an explicit selection, use that
            result = _activePortalPage;
        }
        else
        {
            Collection<Portal.WebPart> activeTabs = getTabs(ctx.getContainer());
            if (activeTabs.isEmpty())
            {
                // No real tabs exist for this folder type, so just use the default portal page
                result = Portal.DEFAULT_PORTAL_PAGE_ID;
            }
            else
            {
                // Use the left-most tab as the default
                result = activeTabs.iterator().next().getName();
            }
        }

        return result;
    }

    @Override @Nullable
    public FolderTab getDefaultTab()
    {
        return _defaultTab == null ? getDefaultTabs().get(0) : _defaultTab;
    }
}
