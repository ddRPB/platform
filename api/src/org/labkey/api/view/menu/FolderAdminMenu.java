/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.api.view.menu;

import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.Container;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: brittp
 * Date: Apr 9, 2007
 * Time: 9:51:12 AM
 */
public class FolderAdminMenu extends NavTreeMenu
{
    public FolderAdminMenu(ViewContext context)
    {
        super(context, "folderAdmin", "Manage Folder", true, getNavTree(context));
    }

    public static NavTree[] getNavTree(ViewContext context)
    {
        Container c = context.getContainer();

        List<NavTree> admin = new ArrayList<NavTree>();
        admin.addAll(getFolderElements(context.getContainer()));
        admin.addAll(Arrays.asList(ProjectAdminMenu.getNavTree(context)));
        NavTree[] adminArr = new NavTree[admin.size()];
        return admin.toArray(adminArr);
    }

    public static ArrayList<NavTree> getFolderElements(Container c)
    {
        ArrayList<NavTree> admin = new ArrayList<NavTree>();
        admin.add(new NavTree("Permissions", PageFlowUtil.urlProvider(SecurityUrls.class).getBeginURL(c)));
        admin.add(new NavTree("Management", PageFlowUtil.urlProvider(AdminUrls.class).getFolderManagementURL(c)));
        return admin;
    }

    @Override
    public boolean isVisible()
    {
        Container c = getViewContext().getContainer();
        Container project = c.getProject();
        User user = getViewContext().getUser();
        return (null != project && project.isProject() && c.hasPermission(user, AdminPermission.class));
    }
}