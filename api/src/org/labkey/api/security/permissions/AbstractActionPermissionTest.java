package org.labkey.api.security.permissions;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.action.PermissionCheckableAction;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.roles.ApplicationAdminRole;
import org.labkey.api.security.roles.AuthorRole;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.security.roles.SubmitterRole;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractActionPermissionTest extends Assert
{
    private static final String SITE_ADMIN_EMAIL = "sadmin@actionpermission.test";
    private static final String APPLICATION_ADMIN_EMAIL = "aadmin@actionpermission.test";
    private static final String PROJECT_ADMIN_EMAIL = "padmin@actionpermission.test";
    private static final String FOLDER_ADMIN_EMAIL = "fadmin@actionpermission.test";
    private static final String EDITOR_EMAIL = "editor@actionpermission.test";
    private static final String AUTHOR_EMAIL = "author@actionpermission.test";
    private static final String READER_EMAIL = "reader@actionpermission.test";
    private static final String SUBMITTER_EMAIL = "submitter@actionpermission.test";
    private static final String[] ALL_EMAILS = {
            SITE_ADMIN_EMAIL, APPLICATION_ADMIN_EMAIL, PROJECT_ADMIN_EMAIL,
            FOLDER_ADMIN_EMAIL, EDITOR_EMAIL, AUTHOR_EMAIL, READER_EMAIL, SUBMITTER_EMAIL
    };

    private static Container _c;
    private static Map<String, User> _users;

    @BeforeClass
    public static void initialize()
    {
        cleanupUsers(ALL_EMAILS);
        Container junit = JunitUtil.getTestContainer();
        _c = ContainerManager.createContainer(junit, "ActionPermissionTest-" + GUID.makeGUID());
        _users = createUsers(ALL_EMAILS);

        MutableSecurityPolicy policy = new MutableSecurityPolicy(_c, _c.getPolicy());
        policy.addRoleAssignment(_users.get(SITE_ADMIN_EMAIL), RoleManager.getRole(SiteAdminRole.class));
        policy.addRoleAssignment(_users.get(APPLICATION_ADMIN_EMAIL), RoleManager.getRole(ApplicationAdminRole.class));
        policy.addRoleAssignment(_users.get(PROJECT_ADMIN_EMAIL), RoleManager.getRole(ProjectAdminRole.class));
        policy.addRoleAssignment(_users.get(FOLDER_ADMIN_EMAIL), RoleManager.getRole(FolderAdminRole.class));
        policy.addRoleAssignment(_users.get(EDITOR_EMAIL), RoleManager.getRole(EditorRole.class));
        policy.addRoleAssignment(_users.get(AUTHOR_EMAIL), RoleManager.getRole(AuthorRole.class));
        policy.addRoleAssignment(_users.get(READER_EMAIL), RoleManager.getRole(ReaderRole.class));
        policy.addRoleAssignment(_users.get(SUBMITTER_EMAIL), RoleManager.getRole(SubmitterRole.class));
        SecurityPolicyManager.savePolicy(policy);
    }

    @AfterClass
    public static void tearDown()
    {
        assertTrue(ContainerManager.delete(_c, TestContext.get().getUser()));
        cleanupUsers(ALL_EMAILS);
    }

    @Test
    public abstract void testActionPermissions();

    private static void cleanupUsers(String[] userEmails)
    {
        //clean up users in case this failed part way through
        try
        {
            for (String email : userEmails)
            {
                User oldUser = UserManager.getUser(new ValidEmail(email));
                if (null != oldUser)
                    UserManager.deleteUser(oldUser.getUserId());
            }
        }
        catch(Exception e)
        {}
    }

    private static Map<String, User> createUsers(String[] userEmails)
    {
        Map<String, User> users = new HashMap<>();

        try
        {
            for (String email : userEmails)
            {
                User user = SecurityManager.addUser(new ValidEmail(email), null).getUser();
                users.put(email, user);
            }
        }
        catch(Exception e)
        {}

        return users;
    }

    public void assertForReadPermission(User user, PermissionCheckableAction... actions)
    {
        for (PermissionCheckableAction action : actions)
        {
            assertPermission(action, user);

            assertPermission(action,
                _users.get(READER_EMAIL), _users.get(AUTHOR_EMAIL), _users.get(EDITOR_EMAIL),
                _users.get(FOLDER_ADMIN_EMAIL), _users.get(PROJECT_ADMIN_EMAIL),
                _users.get(APPLICATION_ADMIN_EMAIL), _users.get(SITE_ADMIN_EMAIL)
            );

            assertNoPermission(action,
                _users.get(SUBMITTER_EMAIL)
            );
        }
    }

    public void assertForInsertPermission(User user, PermissionCheckableAction... actions)
    {
        for (PermissionCheckableAction action : actions)
        {
            assertPermission(action, user);

            assertPermission(action,
                _users.get(SUBMITTER_EMAIL), _users.get(AUTHOR_EMAIL),
                _users.get(EDITOR_EMAIL), _users.get(FOLDER_ADMIN_EMAIL),
                _users.get(PROJECT_ADMIN_EMAIL), _users.get(APPLICATION_ADMIN_EMAIL),
                _users.get(SITE_ADMIN_EMAIL)
            );

            assertNoPermission(action,
                _users.get(READER_EMAIL)
            );
        }
    }

    public void assertForUpdateOrDeletePermission(User user, PermissionCheckableAction... actions)
    {
        for (PermissionCheckableAction action : actions)
        {
            assertPermission(action, user);

            assertPermission(action,
                _users.get(EDITOR_EMAIL), _users.get(FOLDER_ADMIN_EMAIL),
                _users.get(PROJECT_ADMIN_EMAIL), _users.get(APPLICATION_ADMIN_EMAIL),
                _users.get(SITE_ADMIN_EMAIL)
            );

            assertNoPermission(action,
                _users.get(SUBMITTER_EMAIL), _users.get(READER_EMAIL), _users.get(AUTHOR_EMAIL)
            );
        }
    }

    public void assertForAdminPermission(User user, PermissionCheckableAction... actions)
    {
        for (PermissionCheckableAction action : actions)
        {
            assertPermission(action, user);

            assertPermission(action,
                _users.get(FOLDER_ADMIN_EMAIL), _users.get(PROJECT_ADMIN_EMAIL),
                _users.get(APPLICATION_ADMIN_EMAIL), _users.get(SITE_ADMIN_EMAIL)
            );

            assertNoPermission(action,
                _users.get(SUBMITTER_EMAIL), _users.get(READER_EMAIL),
                _users.get(AUTHOR_EMAIL), _users.get(EDITOR_EMAIL)
            );
        }
    }

    public void assertForAdminOperationsPermission(User user, PermissionCheckableAction... actions)
    {
        for (PermissionCheckableAction action : actions)
        {
            assertPermission(action, user);

            assertPermission(action,
                _users.get(SITE_ADMIN_EMAIL)
            );

            assertNoPermission(action,
                _users.get(SUBMITTER_EMAIL), _users.get(READER_EMAIL),
                _users.get(AUTHOR_EMAIL), _users.get(EDITOR_EMAIL),
                _users.get(FOLDER_ADMIN_EMAIL), _users.get(PROJECT_ADMIN_EMAIL),
                _users.get(APPLICATION_ADMIN_EMAIL)
            );
        }
    }

    public void assertForUserManagementPermission(User user, PermissionCheckableAction... actions)
    {
        for (PermissionCheckableAction action : actions)
        {
            assertPermission(action, user);

            assertPermission(action,
                _users.get(APPLICATION_ADMIN_EMAIL), _users.get(SITE_ADMIN_EMAIL)
            );

            assertNoPermission(action,
                _users.get(SUBMITTER_EMAIL), _users.get(READER_EMAIL),
                _users.get(AUTHOR_EMAIL), _users.get(EDITOR_EMAIL),
                _users.get(FOLDER_ADMIN_EMAIL), _users.get(PROJECT_ADMIN_EMAIL)
            );
        }
    }

    private void assertPermission(PermissionCheckableAction action, User... users)
    {
        for (User u : users)
        {
            action.setViewContext(makeContext(u));

            try
            {
                action.checkPermissions();
            }
            catch (UnauthorizedException x)
            {
                fail("Should have permission: user (" + u.getEmail() + "), action (" + action.getClass().getName() + ")");
            }
        }
    }

    private void assertNoPermission(PermissionCheckableAction action, User... users)
    {
        for (User u : users)
        {
            action.setViewContext(makeContext(u));

            try
            {
                action.checkPermissions();
                fail("Should not have permission: user (" + u.getEmail() + "), action (" + action.getClass().getName() + ")");
            }
            catch (UnauthorizedException x)
            {
                // expected
            }
        }
    }

    private ViewContext makeContext(User u)
    {
        HttpServletRequest w = new HttpServletRequestWrapper(TestContext.get().getRequest())
        {
            @Override
            public String getParameter(String name)
            {
                if (CSRFUtil.csrfName.equals(name))
                    return CSRFUtil.getExpectedToken(TestContext.get().getRequest(), null);
                return super.getParameter(name);
            }
        };

        ViewContext context = new ViewContext();
        context.setContainer(_c);
        context.setUser(u);
        context.setRequest(w);
        return context;
    }
}
