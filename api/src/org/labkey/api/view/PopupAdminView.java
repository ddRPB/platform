/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.api.view;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.AdminReadPermission;
import org.labkey.api.view.menu.FolderAdminMenu;
import org.labkey.api.view.menu.ProjectAdminMenu;
import org.labkey.api.view.menu.SiteAdminMenu;

import java.io.PrintWriter;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * User: Mark Igra
 * Date: Jun 21, 2007
 * Time: 10:48:42 AM
 */
public class PopupAdminView extends PopupMenuView
{
    private boolean visible;

    protected void renderInternal(PopupMenu model, PrintWriter out) throws Exception
    {
        if (visible)
            super.renderInternal(model, out);
        else
            out.write("&nbsp;");
    }

    public PopupAdminView(final ViewContext context)
    {
        Container c = context.getContainer();

        // If current context is a container tab, use the parent container to build this menu
        if (c.isContainerTab())
        {
            c = c.getParent();
            context.setContainer(c);
        }

        User user = context.getUser();

        boolean isAdminInThisFolder = context.hasPermission("PopupAdminView", AdminPermission.class);
        boolean hasAdminReadInRoot = ContainerManager.getRoot().hasPermission(user, AdminReadPermission.class);

        visible = isAdminInThisFolder || hasAdminReadInRoot;
        if (!visible)
            return;
        
        NavTree navTree = new NavTree("Admin");

        if (hasAdminReadInRoot)
        {
            NavTree siteAdmin = new NavTree("Site");
            siteAdmin.addChildren(SiteAdminMenu.getNavTree(context));
            navTree.addChild(siteAdmin);
        }

        if (!c.isRoot())
        {
            Container project = c.getProject();
            assert project != null;

            if (isAdminInThisFolder && !c.isWorkbook())
            {
                NavTree folderAdmin = new NavTree("Folder");
                folderAdmin.addChildren(FolderAdminMenu.getFolderElements(c));
                folderAdmin.addSeparator();
                folderAdmin.addChildren(ProjectAdminMenu.getNavTree(context));
                navTree.addChild(folderAdmin);
            }
        }

        if (user.isDeveloper())
        {
            NavTree devMenu = new NavTree("Developer Links");
            devMenu.addChildren(PopupDeveloperView.getNavTree(context));
            navTree.addChild(devMenu);
        }

        if (!c.isRoot())
        {
            navTree.addSeparator();
            c.getFolderType().addManageLinks(navTree, c);

            Comparator<Module> moduleComparator = new Comparator<Module>()
            {
                public int compare(Module o1, Module o2)
                {
                    if (null == o1 && null == o2)
                        return 0;
                    if (null == o1 || null == o2)
                        return null == o1 ? -1 : 1;
                    return o1.getTabName(context).compareToIgnoreCase(o2.getTabName(context));
                }
            };

            SortedSet<Module> activeModules = new TreeSet<Module>(moduleComparator);
            activeModules.addAll(c.getActiveModules());
            SortedSet<Module> disabledModules = new TreeSet<Module>(moduleComparator);
            disabledModules.addAll(ModuleLoader.getInstance().getModules());
            disabledModules.removeAll(activeModules);

            NavTree goToModuleMenu = new NavTree("Go To Module");
            Module defaultModule = null;

            if (c.getFolderType() != FolderType.NONE)
            {
                defaultModule = c.getFolderType().getDefaultModule();
                goToModuleMenu.addChild(c.getName() + " Start Page", c.getFolderType().getStartURL(c, user));
            }

            addModulesToMenu(context, activeModules, defaultModule, goToModuleMenu);

            if (!disabledModules.isEmpty())
            {
                NavTree disabledModuleMenu = new NavTree("More Modules");
                addModulesToMenu(context, disabledModules, defaultModule, disabledModuleMenu);
                if (disabledModuleMenu.hasChildren())
                {
                    goToModuleMenu.addSeparator();
                    goToModuleMenu.addChild(disabledModuleMenu);
                }
            }

            if (goToModuleMenu.hasChildren())
            {
                navTree.addSeparator();
                navTree.addChild(goToModuleMenu);
            }
        }

        navTree.setId("adminMenu");
        setNavTree(navTree);
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.TEXT);
        setExtVersion("Ext4");
    }

    private void addModulesToMenu(ViewContext context, SortedSet<Module> modules, Module defaultModule, NavTree menu)
    {
        for (Module module : modules)
        {
            if (null == module || module.equals(defaultModule))
                continue;

            ActionURL tabUrl = module.getTabURL(context.getContainer(), context.getUser());

            if (null != tabUrl)
                menu.addChild(module.getTabName(context), tabUrl);
        }
    }
}
