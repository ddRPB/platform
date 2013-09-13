/*
 * Copyright (c) 2004-2013 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.ACL;
import org.labkey.api.security.HasPermission;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.WebPartFactory;
import org.springframework.validation.BindException;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * see ContainerManager for more info
 *
 * CONSIDER: extend org.labkey.api.data.Entity
 */
public class Container implements Serializable, Comparable<Container>, SecurableResource, ContainerContext, HasPermission
{
    private GUID _id;
    private Path _path;
    private Date _created;
    private int _rowId; //Unique for this installation

    /** Used to arbitrarily reorder siblings within a container. */
    private int _sortOrder;
    private String _description;

    private transient Module _defaultModule;

    private transient WeakReference<Container> _parent;

    public static final String DEFAULT_SUPPORT_PROJECT_PATH = ContainerManager.HOME_PROJECT_PATH + "/support";

    public enum TYPE
    {
        normal,
        workbook,
        tab;

        public static TYPE typeFromString(String s)
        {
            if (null != s)
            {
                if (s.equalsIgnoreCase("workbook")) return workbook;
                if (s.equalsIgnoreCase("tab")) return tab;
            }
            return normal;
        }

    }

    //is this container a workbook, tab or normal?
    private TYPE _type;

    // include in results from searches outside this container?
    private final boolean _searchable;

    //optional non-unique title for the container
    private String _title;

    // UNDONE: BeanFactory for Container

    protected Container(Container dirParent, String name, String id, int rowId, int sortOrder, Date created, boolean searchable)
    {
        _path = null == dirParent && StringUtils.isEmpty(name) ? Path.rootPath : ContainerManager.makePath(dirParent, name);
        _id = new GUID(id);
        _parent = new WeakReference<>(dirParent);
        _rowId = rowId;
        _sortOrder = sortOrder;
        _created = created;
        _searchable = searchable;
    }


    public Container getContainer(Map context)
    {
        return this;
    }


    @NotNull
    public String getName()
    {
        return _path.getName();
    }

    @NotNull
    public String getResourceName()
    {
        return _path.getName();
    }

    public Date getCreated()
    {
        return _created;
    }


    public boolean isInheritedAcl()
    {
        return !(getPolicy().getResourceId().equals(getId()));
    }

    /**
     * @return the parent container, or the root container (with path "/") if called on the root
     */
    public Container getParent()
    {
        Container parent = _parent == null ? null : _parent.get();
        if (null == parent && _path.size() > 0)
        {
            parent = ContainerManager.getForPath(_path.getParent());
            _parent = new WeakReference<>(parent);
        }
        return parent;
    }

    /**
     * @return the unencoded container path ignoring "/"
     */
    public String getPath()
    {
        return _path.toString("/","");
    }


    public Path getParsedPath()
    {
        return _path;
    }


    /**
     * returned string begins and ends with "/" so you can slap it between
     * getContextPath() and action.view
     */
    public String getEncodedPath()
    {
        return _path.encode("/","/");
    }


    public String getId()
    {
        return _id.toString();
    }

    public GUID getEntityId()
    {
        return _id;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getSortOrder()
    {
        return _sortOrder;
    }

    public void setSortOrder(int sortOrder)
    {
        _sortOrder = sortOrder;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    /**
     * Get the project Container or null if isRoot().
     * @return The project Container or null if isRoot().
     */
    public @Nullable Container getProject()
    {
        // Root has no project
        if (isRoot())
            return null;

        Container project = this;
        while (!project.isProject())
        {
            project = project.getParent();
            if (null == project)        // deleted container?
                return null;
        }
        return project;
    }


    // Note: don't use the security policy directly unless you really have to... call hasPermission() or hasOneOf()
    // instead, to ensure proper behavior during impersonation.
    public @NotNull SecurityPolicy getPolicy()
    {
        return SecurityPolicyManager.getPolicy(this);
    }


    public boolean hasPermission(String logMsg, @NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (user instanceof User && isForbiddenProject((User)user))
            return false;
        return getPolicy().hasPermission(logMsg, user, perm);
    }


    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (user instanceof User && isForbiddenProject((User)user))
            return false;
        return getPolicy().hasPermission(user, perm);
    }


    public boolean hasPermission(String logMsg, @NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm, @Nullable Set<Role> contextualRoles)
    {
        if (user instanceof User && isForbiddenProject((User)user))
            return false;
        return getPolicy().hasPermission(logMsg, user, perm, contextualRoles);
    }


    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm, @Nullable Set<Role> contextualRoles)
    {
        if (user instanceof User && isForbiddenProject((User)user))
            return false;
        return getPolicy().hasPermission(user, perm, contextualRoles);
    }

    public boolean hasOneOf(@NotNull User user, @NotNull Collection<Class<? extends Permission>> perms)
    {
        return !isForbiddenProject(user) && getPolicy().hasOneOf(user, perms, null);
    }

    public boolean hasOneOf(@NotNull User user, @NotNull Class<? extends Permission>... perms)
    {
        return hasOneOf(user, Arrays.asList(perms));
    }


    /**
     * Don't use this anymore
     * @param user the user
     * @param perm the old nasty integer permission
     * @return something you don't want anymore
     * @deprecated Use hasPermission(User user, Class&lt;? extends Permission&gt; perm) instead
     */
    @Deprecated
    public boolean hasPermission(User user, int perm)
    {
        if (isForbiddenProject(user))
            return false;

        SecurityPolicy policy = getPolicy();
        return policy.hasPermissions(user, getPermissionsForIntPerm(perm));
    }

    @Deprecated
    private Set<Class<? extends Permission>> getPermissionsForIntPerm(int perm)
    {
        Set<Class<? extends Permission>> perms = new HashSet<>();
        if ((perm & ACL.PERM_READ) > 0 || (perm & ACL.PERM_READOWN) > 0)
            perms.add(ReadPermission.class);
        if ((perm & ACL.PERM_INSERT) > 0)
            perms.add(InsertPermission.class);
        if ((perm & ACL.PERM_UPDATE) > 0 || (perm & ACL.PERM_UPDATEOWN) > 0)
            perms.add(UpdatePermission.class);
        if ((perm & ACL.PERM_DELETE) > 0 || (perm & ACL.PERM_DELETEOWN) > 0)
            perms.add(DeletePermission.class);
        if ((perm & ACL.PERM_ADMIN) > 0)
            perms.add(AdminPermission.class);

        return perms;
    }

    public boolean isForbiddenProject(User user)
    {
        if (null != user)
        {
            @Nullable Container impersonationProject = user.getImpersonationProject();

            // Root is never forbidden (site admin case), otherwise, impersonation project must match current project
            if (null != impersonationProject && !impersonationProject.equals(getProject()))
                return true;
        }

        return false;
    }


    public boolean isProject()
    {
        return _path.size() == 1;
    }


    public boolean isRoot()
    {
        return _path.size() == 0;
    }


    public boolean shouldDisplay(User user)
    {
        if (isWorkbookOrTab())                          // TODO: still seems right, can't navigate directly to it
            return false;

        String name = _path.getName();
        if (name.length() == 0)
            return true; // Um, I guess we should display it?
        char c = name.charAt(0);
        if (c == '_' || c == '.')
        {
            return user != null && (user.isSiteAdmin() || hasPermission(user, AdminPermission.class));
        }
        else
        {
            return true;
        }
    }


    public boolean isWorkbook()
    {
        return _type == TYPE.workbook;
    }

    public boolean isWorkbookOrTab()
    {
        return TYPE.workbook == _type || TYPE.tab == _type;
    }

    Boolean hasWorkbookChildren = null;

    public synchronized boolean hasWorkbookChildren()
    {
        if (null == hasWorkbookChildren)
        {
            hasWorkbookChildren = false;
            for (Container ch : getChildren())
            {
                if (ch.isWorkbook())
                {
                    hasWorkbookChildren = true;
                    break;
                }
            }
        }
        return hasWorkbookChildren;
    }

    public boolean isSearchable()
    {
        return _searchable;
    }

    /**
     * Returns true if possibleAncestor is a parent of this container,
     * or a parent-of-a-parent, etc.
     */
    public boolean hasAncestor(Container possibleAncestor)
    {
        if (isRoot())
            return false;
        if (getParent().equals(possibleAncestor))
            return true;
        return getParent().hasAncestor(possibleAncestor);
    }

    public Container getChild(String folderName)
    {
        return ContainerManager.getChild(this,folderName);
    }

    public boolean hasChild(String folderName)
    {
        return getChild(folderName) != null;
    }

    public boolean hasChildren()
    {
        return getChildren().size() > 0;
    }

    public List<Container> getChildren()
    {
        return ContainerManager.getChildren(this);
    }

    @NotNull
    public List<SecurableResource> getChildResources(User user)
    {
        List<SecurableResource> ret = new ArrayList<>();

        //add all sub-containers the user is allowed to read
        ret.addAll(ContainerManager.getChildren(this, user, ReadPermission.class));

        //add resources from study
        StudyService.Service sts = ServiceRegistry.get().getService(StudyService.Service.class);
        if (null != sts)
            ret.addAll(sts.getSecurableResources(this, user));

        //add report descriptors
        //this seems much more cumbersome than it should be
        Report[] reports = ReportService.get().getReports(user, this);
        for(Report report : reports)
        {
            SecurityPolicy policy = SecurityPolicyManager.getPolicy(report.getDescriptor());
            if (policy.hasPermission(user, AdminPermission.class))
                ret.add(report.getDescriptor());
        }

        //add pipeline root
        PipeRoot root = PipelineService.get().findPipelineRoot(this);
        if (null != root)
        {
            SecurityPolicy policy = SecurityPolicyManager.getPolicy(root);
            if (policy.hasPermission(user, AdminPermission.class))
                ret.add(root);
        }

        if (isRoot())
        {
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

            if (null != ss)
            {
                ret.addAll(ss.getSecurableResources(user));
            }
        }

        return ret;
    }

    /**
     * Finds a securable resource within this container or child containers with the same id
     * as the given resource id.
     * @param resourceId The resource id to find
     * @param user The current user (searches only resources that user can see)
     * @return The resource or null if not found
     */
    @Nullable
    public SecurableResource findSecurableResource(String resourceId, User user)
    {
        if (null == resourceId)
            return null;

        if (getResourceId().equals(resourceId))
            return this;

        //recurse down all non-container resources
        SecurableResource resource = findSecurableResourceInContainer(resourceId, user, this);
        if (null != resource)
            return resource;

        //recurse down child containers
        for(Container child : getChildren())
        {
            //only look in child containers where the user has read perm
            if (child.hasPermission(user, ReadPermission.class))
            {
                resource = child.findSecurableResource(resourceId, user);

                if (null != resource)
                    return resource;
            }
        }

        return null;
    }

    protected SecurableResource findSecurableResourceInContainer(String resourceId, User user, SecurableResource parent)
    {
        for (SecurableResource child : parent.getChildResources(user))
        {
            if (child instanceof Container)
                continue;

            if (child.getResourceId().equals(resourceId))
                return child;

            SecurableResource resource = findSecurableResourceInContainer(resourceId, user, child);

            if (null != resource)
                return resource;
        }

        return null;
    }


    public String toString()
    {
        return getClass().getName() + "@" + System.identityHashCode(this) + " " + _path + " " + _id;
    }


    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Container container = (Container) o;

        return !(_id != null ? !_id.equals(container._id) : container._id != null);
    }


    public int hashCode()
    {
        return _id.hashCode();
    }


    public static boolean isLegalName(String name, StringBuilder error)
    {
        if (null == name || 0 == name.trim().length())
        {
            error.append("Blank names are not allowed.");
            return false;
        }

        if (name.length() > 255)
        {
            error.append("Folder name must be shorter than 255 characters");
            return false;
        }

        if (!FileUtil.isLegalName(name))
        {
            error.append("Folder name must be a legal filename and not contain one of '/', '\\', ':', '?', '<', '>', '*', '|', '\"', '^'");
            return false;
        }

        if (-1 != name.indexOf(';'))
        {
            error.append("Semicolons are not allowed in folder names.");
            return false;
        }
        else if (name.startsWith("@"))
        {
            error.append("Folder name may not begin with '@'.");
            return false;
        }

        //Don't allow ISOControl characters as they are not handled well by the databases
        for( int i = 0; i < name.length(); ++i)
        {
            if (Character.isISOControl(name.charAt(i)))
            {
                error.append("Non-printable characters are not allowed in folder names.");
                return false;
            }
        }

        return true;
    }

    public static boolean isLegalTitle(String name, StringBuilder error)
    {
        if (null == name || 0 == name.trim().length())
        {
            return true;  //titles can be blank
        }

        if (name.length() > 1000)
        {
            error.append("Title must be shorter than 1000 characters");
            return false;
        }

        //Don't allow ISOControl characters as they are not handled well by the databases
        for( int i = 0; i < name.length(); ++i)
        {
            if (Character.isISOControl(name.charAt(i)))
            {
                error.append("Non-printable characters are not allowed in titles.");
                return false;
            }
        }

        return true;
    }

    public @NotNull ActionURL getStartURL(User user)
    {
        FolderType ft = getFolderType();
        if (!FolderType.NONE.equals(ft))
            return ft.getStartURL(this, user);

        Module module = getDefaultModule();
        if (module != null)
        {
            ActionURL helper = module.getTabURL(this, user);
            if (helper != null)
                return helper;
        }

        return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(this);
    }

    public Module getDefaultModule()
    {
        if (isRoot())
            return null;

        if (_defaultModule == null)
        {
            Map props = PropertyManager.getProperties(this, "defaultModules");
            String defaultModuleName = (String) props.get("name");

            boolean initRequired = false;
            if (null == defaultModuleName || null == ModuleLoader.getInstance().getModule(defaultModuleName))
            {
                defaultModuleName = "Core";
                initRequired = true;
            }
            Module defaultModule = ModuleLoader.getInstance().getModule(defaultModuleName);

            //set default module
            if (initRequired)
                setDefaultModule(defaultModule);

            //ensure that default module is included in active module set
            //should be there already if it's not portal, but if it is core, we have to add it for upgrade
            if (defaultModuleName.compareToIgnoreCase("Core") == 0)
            {
                Set<Module> modules = new HashSet<>(getActiveModules());
                if (!modules.contains(defaultModule))
                {
                    modules.add(defaultModule);
                    setActiveModules(modules);
                }
            }

            _defaultModule = defaultModule;
        }
        return _defaultModule;
    }

    public void setFolderType(FolderType folderType, Set<Module> ensureModules, boolean brandNew)
    {
        BindException errors = new BindException(new Object(), "dummy");
        setFolderType(folderType, ensureModules, brandNew, errors);
    }

    public void setFolderType(FolderType folderType, Set<Module> ensureModules, boolean brandNew, BindException errors)
    {
        setFolderType(folderType, ModuleLoader.getInstance().getUpgradeUser(), brandNew, errors);
        if (!errors.hasErrors())
        {
            Set<Module> modules = new HashSet<>(folderType.getActiveModules());
            modules.addAll(ensureModules);
            setActiveModules(modules);
        }
    }

    public void setFolderType(FolderType folderType, User user, boolean brandNew)
    {
        BindException errors = new BindException(new Object(), "dummy");
        setFolderType(folderType, user, brandNew, errors);
    }

    public void setFolderType(FolderType folderType, User user, boolean brandNew, BindException errors)
    {
        ContainerManager.setFolderType(this, folderType, user, brandNew, errors);

        if (!errors.hasErrors())
        {
            if (isWorkbook())
                appendWorkbookModulesToParent();
        }
    }

    public Set<Module> getRequiredModules()
    {
        Set<Module> requiredModules = new HashSet<>();
        requiredModules.addAll(getFolderType().getActiveModules());

        for(Container child: getChildren())
        {
            if(child.isWorkbook())
            {
                requiredModules.addAll(child.getFolderType().getActiveModules());
            }
        }

        return requiredModules;
    }

    @NotNull
    public FolderType getFolderType()
    {
        return ContainerManager.getFolderType(this);
    }

    /**
     * Sets the default module for a "mixed" type folder. We try not to create
     * these any more. Instead each folder is "owned" by a module
     */
    public void setDefaultModule(Module module)
    {
        if (module == null)
            return;
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(this, "defaultModules", true);
        props.put("name", module.getName());

        PropertyManager.saveProperties(props);
        ContainerManager.notifyContainerChange(getId());
        _defaultModule = null;
    }


    public void setActiveModules(Set<Module> modules)
    {
        if(isWorkbook())
        {
            appendWorkbookModulesToParent(modules);
            return;
        }

        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(this, "activeModules", true);
        props.clear();
        for (Module module : modules)
        {
            if (null != module)
                props.put(module.getName(), Boolean.TRUE.toString());
        }

        for (Module module : getRequiredModules())
        {
            if (null != module)
                props.put(module.getName(), Boolean.TRUE.toString());
        }

        PropertyManager.saveProperties(props);
        ContainerManager.notifyContainerChange(getId());
    }

    public void appendWorkbookModulesToParent()
    {
        appendWorkbookModulesToParent(new HashSet<Module>());
    }

    public void appendWorkbookModulesToParent(Set<Module> newModules)
    {
        if(!isWorkbook())
            return;

        boolean isChanged = false;
        Set<Module> existingModules = new HashSet<>();
        existingModules.addAll(getParent().getActiveModules(false, false));

        newModules.addAll(getFolderType().getActiveModules());

        for(Module m : newModules)
        {
            if(!existingModules.contains(m))
            {
                isChanged = true;
                existingModules.add(m);
            }
        }

        if(isChanged)
        {
            getParent().setActiveModules(existingModules);
        }
    }

    // UNDONE: (MAB) getActiveModules() and setActiveModules()
    // UNDONE: these don't feel like they belong on this class
    // UNDONE: move to ModuleLoader?
    public Set<Module> getActiveModules()
    {
        return getActiveModules(false, true);
    }

    public Set<Module> getActiveModules(boolean init)
    {
        return getActiveModules(init, true);
    }

    public Set<Module> getActiveModules(boolean init, boolean includeDepencendies)
    {
        if(isWorkbook())
        {
            if(init)
                appendWorkbookModulesToParent();

            return getParent().getActiveModules(init, includeDepencendies);
        }

        //Short-circuit for root module
        if (isRoot())
        {
            //get active modules from database
            Set<Module> modules = new HashSet<>();
            return Collections.unmodifiableSet(modules);
        }

        Map<String, String> props = PropertyManager.getProperties(this, "activeModules");
        //get set of all modules
        List<Module> allModules = ModuleLoader.getInstance().getModules();

        //get active web parts for this container
        List<Portal.WebPart> activeWebparts = Portal.getParts(this);

        // store active modules, checking first that the container still exists -- junit test creates and deletes
        // containers quickly and this check helps keep the search indexer from creating orphaned property sets.
        if (props.isEmpty() && init && null != ContainerManager.getForId(getId()))
        {
            //initialize properties cache
            PropertyManager.PropertyMap propsWritable = PropertyManager.getWritableProperties(this, "activeModules", true);
            props = propsWritable;

            if (isProject())
            {
                // first time in this project: initialize active modules now, based on the active webparts
                Map<String, Module> mapWebPartModule = new HashMap<>();
                //get set of all web parts for all modules
                for (Module module : allModules)
                {
                    for (WebPartFactory desc : module.getWebPartFactories())
                        mapWebPartModule.put(desc.getName(), module);
                }

                //get active modules based on which web parts are active
                for (Portal.WebPart activeWebPart : activeWebparts)
                {
                    if (!"forward".equals(activeWebPart.getLocation()))
                    {
                        //get module associated with this web part & add to props
                        Module activeModule = mapWebPartModule.get(activeWebPart.getName());
                        if (activeModule != null)
                            propsWritable.put(activeModule.getName(), Boolean.TRUE.toString());
                    }
                }

                // enable 'default' tabs:
                for (Module module : allModules)
                {
                    if (module.getTabDisplayMode() == Module.TabDisplayMode.DISPLAY_USER_PREFERENCE_DEFAULT)
                        propsWritable.put(module.getName(), Boolean.TRUE.toString());
                }
            }
            else
            {
                //if this is a subfolder, set active modules to inherit from parent
                Set<Module> parentModules = getParent().getActiveModules(false, false);
                for (Module module : parentModules)
                {
                    //set the default module for the subfolder to be the default module of the parent.
                    Module parentDefault = getParent().getDefaultModule();

                    if (module.equals(parentDefault))
                        setDefaultModule(module);

                    propsWritable.put(module.getName(), Boolean.TRUE.toString());
                }
            }
            PropertyManager.saveProperties(propsWritable);
        }

        Set<Module> modules = new HashSet<>();
        // add all modules found in user preferences:
        if (null != props)
        {
            for (String moduleName : props.keySet())
            {
                Module module = ModuleLoader.getInstance().getModule(moduleName);
                if (module != null)
                    modules.add(module);
            }
        }

       // ensure all modules for folder type are added (may have been added after save
        if (!getFolderType().equals(FolderType.NONE))
        {
            for (Module module : getFolderType().getActiveModules())
            {
                // check for null, since there's no guarantee that a third-party folder type has all its
                // active modules installed on this system (so nulls may end up in the list- bug 6757):
                if (module != null)
                    modules.add(module);
            }
        }

        // Container tab inherits from parent
/*        if (isContainerTab())
        {
            for (Module module : getParent().getActiveModules())
                if (null != module)
                    modules.add(module);
        }   */

        // add all 'always display' modules, remove all 'never display' modules:
        for (Module module : allModules)
        {
            if (module.getTabDisplayMode() == Module.TabDisplayMode.DISPLAY_NEVER)
                modules.remove(module);
        }

        Set<Module> activeModules;
        if(includeDepencendies)
        {
            Set<Module> withDependencies = new HashSet<>();
            for (Module m : modules)
            {
                withDependencies.add(m);
                withDependencies.addAll(m.getResolvedModuleDependencies());
            }

            activeModules = Collections.unmodifiableSet(withDependencies);
        }
        else
        {
            activeModules = Collections.unmodifiableSet(modules);
        }

        return activeModules;
    }

    public boolean isDescendant(Container container)
    {
        if (null == container)
            return false;

        Container cur = getParent();
        while (null != cur)
        {
            if (cur.equals(container))
                return true;
            cur = cur.getParent();
        }
        return false;
    }

    public Map<String, Set<String>> getModuleDependencyMap()
    {
        Map<String, Set<String>> dependencies = new HashMap<>();

        for (Module m : ModuleLoader.getInstance().getModules())
        {
            for (Module dm : m.getResolvedModuleDependencies())
            {
                String name = dm.getName(); //modules can declare a dependency using the wrong case, so we normalize
                if(dependencies.get(name) == null)
                    dependencies.put(name, new HashSet<String>());

                dependencies.get(name).add(m.getName());
            }
        }

        return dependencies;
    }

    /**
     * Searches descendants of this container recursively until it finds
     * one that has a name matching the name provided. Search is done
     * breadth-first (optimize for immediate child), and name matching is
     * case-insensitive.
     * @param name The name to find
     * @return Matching Container or null if not found.
     */
    @Nullable
    public Container findDescendant(String name)
    {
        return findDescendant(this, name);
    }

    private Container findDescendant(Container parent, String name)
    {
        for (Container child : parent.getChildren())
        {
            if (child.getName().equalsIgnoreCase(name))
                return child;
        }

        Container ret = null;
        for (Container child : parent.getChildren())
        {
            ret = findDescendant(child, name);
            if (null == ret)
                return ret;
        }
        return null;
    }

    public Map<String, Object> toJSON(User user)
    {
        return toJSON(user, true);
    }

    public Map<String, Object> toJSON(User user, boolean includePermissions)
    {
        Map<String, Object> containerProps = new HashMap<>();
        containerProps.put("name", getName());
        containerProps.put("id", getId());
        containerProps.put("path", getPath());
        containerProps.put("sortOrder", getSortOrder());
        if (includePermissions)
        {
            containerProps.put("userPermissions", getPolicy().getPermsAsOldBitMask(user));
            containerProps.put("effectivePermissions", getPolicy().getPermissionNames(user));
        }
        if (null != getDescription())
            containerProps.put("description", getDescription());
        containerProps.put("isWorkbook", isWorkbook());
        containerProps.put("isContainerTab", isContainerTab());
        containerProps.put("type", getContainerNoun());
        JSONArray activeModuleNames = new JSONArray();
        for (Module module : getActiveModules())
        {
            activeModuleNames.put(module.getName());
        }
        containerProps.put("activeModules", activeModuleNames);
        containerProps.put("folderType", getFolderType().getName());

        Container parent = getParent();
        containerProps.put("parentPath", parent==null ? null : parent.getPath());
        containerProps.put("parentId", parent==null ? null : parent.getId());


        if (null != getTitle())
            containerProps.put("title", getTitle());

        return containerProps;
    }

    public boolean isContainerTab()
    {
        return _type == TYPE.tab;
    }

    public TYPE getType()
    {
        return _type;
    }

    public void setType(TYPE type)
    {
        _type = type;
    }

    public void setType(String typeString)
    {
        _type = TYPE.typeFromString(typeString);
    }

    public static class ContainerException extends Exception
    {
        public ContainerException(String message)
        {
            super(message);
        }

        public ContainerException(String message, Throwable t)
        {
            super(message, t);
        }
    }

    private int compareSiblings(Container c1, Container c2)
    {
        int result = c1.getSortOrder() - c2.getSortOrder();
        if (result != 0)
            return result;
        return c1.getName().compareToIgnoreCase(c2.getName());
    }

    // returns in order from the root (e.g. /project/folder/)
    private List<Container> getPathAsList()
    {
        List<Container> containerList = new ArrayList<>();
        Container current = this;
        while (!current.isRoot())
        {
            containerList.add(current);
            current = current.getParent();
        }
        Collections.reverse(containerList);
        return containerList;
    }

    public int compareTo(Container other)
    {
        // Container returns itself as a parent if it's root, so we need to special case that
        if (isRoot())
        {
            if (other.isRoot())
                return 0;
            else
                return -1;
        }
        if (other.isRoot())
        {
            return 1;
        }

        // Special case siblings which is common
        if (getParent().equals(other.getParent()))
        {
            return compareSiblings(this, other);
        }

        List<Container> myPath = getPathAsList();
        List<Container> otherPath = other.getPathAsList();
        for (int i=0; i<Math.min(myPath.size(), otherPath.size()); i++)
        {
            Container myContainer = myPath.get(i);
            Container otherContainer = otherPath.get(i);
            if (myContainer.equals(otherContainer))
                continue;
            return compareSiblings(myContainer, otherContainer);
        }

        // They're equal up to the end, but one is longer. E.g. /a/b/c vs /a/b
        return myPath.size() - otherPath.size();
    }

    @NotNull
    public String getResourceId()
    {
        return _id.toString();
    }

    @NotNull
    public String getResourceDescription()
    {
        return "The folder " + getPath();
    }

    @NotNull
    public Set<Class<? extends Permission>> getRelevantPermissions()
    {
        return RoleManager.BasicPermissions;
    }

    @NotNull
    public Module getSourceModule()
    {
        return ModuleLoader.getInstance().getCoreModule();
    }

    @NotNull
    public Container getResourceContainer()
    {
        return this;
    }

    public SecurableResource getParentResource()
    {
        SecurableResource parent = getParent();
        return this.equals(getParent()) ? null : parent;
    }

    public boolean mayInheritPolicy()
    {
        return true;
    }

    /**
     * Returns the non-unique title for this Container, or the Container's name if a title is not set
     * @return the title
     */
    public String getTitle()
    {
        return null != _title ? _title : getName();
    }

    public void setTitle(String title)
    {
        _title = title;
    }

    public String getContainerNoun()
    {
        return getContainerNoun(false);
    }

    public String getContainerNoun(boolean titleCase)
    {
        String noun = isProject() ? "project" : isWorkbook() ? "workbook" : "folder";
        if(titleCase)
        {
            return noun.substring(0, 1).toUpperCase() + noun.substring(1);
        }

        return noun;
    }

    public List<FolderTab> getDefaultTabs()
    {
        // Filter out any container tabs whose containers have been deleted
        FolderType folderType = getFolderType();
        List<FolderTab> folderTabs = new ArrayList<>();
        for (FolderTab folderTab : folderType.getDefaultTabs())
        {
            if (!folderTab.isContainerTab() || null != ContainerManager.getChild(this, folderTab.getName()))
            {
                folderTabs.add(folderTab);
            }
        }
        return folderTabs;
    }
}
