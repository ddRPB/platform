package org.labkey.api.webdav;

import org.labkey.api.util.FileStream;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.NavTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
     * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 28, 2008
 * Time: 11:42:10 AM
 */
public interface Resource
{
    String getPath();

    String getName();

    String getParentPath();

    boolean exists();

    boolean isCollection();

    Resource find(String name);

    // should really be 'isResource()'
    boolean isFile();

    // TODO move more functionality into interface and remove this method
    File getFile();

    List<String> listNames();

    List<Resource> list();

    Resource parent();

    long getCreated();

    String getCreatedBy();

    long getLastModified();

    String getModifiedBy();

    String getContentType();

    Map<String,?> getProperties();

    FileStream getFileStream(User user) throws IOException;

    InputStream getInputStream(User user) throws IOException;

    long copyFrom(User user, FileStream in) throws IOException;

    long copyFrom(User user, Resource r) throws IOException;

    void moveFrom(User user, Resource r) throws IOException;

    long getContentLength() throws IOException;

    @NotNull
    String getHref(ViewContext context);

    @NotNull
    String getLocalHref(ViewContext context);

    @Nullable
    String getExecuteHref(ViewContext context);

    @Nullable
    String getIconHref();

    String getETag();

    @NotNull
    List<WebdavResolver.History> getHistory();

    @NotNull
    List<NavTree> getActions();

    /** user may read properties of this resource */
    boolean canList(User user);

    /** user may read file stream of this resource */
    boolean canRead(User user);

    boolean canWrite(User user);
    boolean canCreate(User user);
    boolean canCreateCollection(User user); // only on collection can create sub collection
    boolean canDelete(User user);
    boolean canRename(User user);

    // dav methods
    boolean delete(User user) throws IOException;
}
