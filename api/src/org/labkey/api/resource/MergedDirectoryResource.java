package org.labkey.api.resource;

import org.labkey.api.collections.CacheMap;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.Filter;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * User: kevink
 * Date: Mar 13, 2010 9:37:56 AM
 */
public class MergedDirectoryResource extends AbstractResourceCollection
{
    private static final CacheMap<Pair<Resolver, Path>, Map<String, Resource>> CHILDREN_CACHE = new CacheMap<Pair<Resolver, Path>, Map<String, Resource>>(50, "MergedDirectoryResourceCache");

    Resolver _resolver;
    List<File> _dirs;
    Resource[] _additional;
    long versionStamp;
    long versionStampTime;
    long minCacheTime;

    final Object _lock = new Object();

    private class CaseInsensitiveTreeMap<V> extends TreeMap<String,V>
    {
        CaseInsensitiveTreeMap()
        {
            super(new Comparator<String>(){
                    public int compare(String s1, String s2)
                    {
                        return s1.compareToIgnoreCase(s2);
                    }
                });
        }
    }

    public MergedDirectoryResource(Resolver resolver, Path path, List<File> dirs, Resource... addl)
    {
        super(path);
        _resolver = resolver;
        _dirs = dirs;
        _additional = addl;
    }

    public Resource parent()
    {
        return _resolver.lookup(getPath().getParent());
    }

    Map<String, Resource> getChildren()
    {
        synchronized (_lock)
        {
            Pair<Resolver, Path> cacheKey = new Pair(_resolver, getPath());
            Map<String, Resource> children = CHILDREN_CACHE.get(cacheKey);
            if (null == children)
            {
                Map<String, ArrayList<File>> map = new CaseInsensitiveTreeMap<ArrayList<File>>();
                for (File dir : _dirs)
                {
                    if (!dir.isDirectory())
                        continue;
                    File[] files = dir.listFiles();
                    if (files == null)
                        continue;
                    for (File f : files)
                    {
                        String name = f.getName();
                        if (f.isHidden())
                            continue;
//                        if (_resolver.filter(name))
//                            continue;
                        if (!map.containsKey(name))
                            map.put(name, new ArrayList<File>(Arrays.asList(f)));
                        else
                        {
                            // only merge directories together
                            ArrayList<File> existing = map.get(name);
                            if (existing.get(0).isDirectory() && f.isDirectory())
                                existing.add(f);
                        }
                    }
                }
                children = new CaseInsensitiveTreeMap<Resource>();
                for (Map.Entry<String, ArrayList<File>> e : map.entrySet())
                {
                    Path path = getPath().append(e.getKey());
                    ArrayList<File> files = e.getValue();
                    Resource r = files.size() == 1 && files.get(0).isFile() ?
                            new FileResource(_resolver, path, files.get(0)) :
                            new MergedDirectoryResource(_resolver, path, files);
                    children.put(e.getKey(), r);
                }
                for (Resource r : _additional)
                    children.put(r.getName(),r);

                CHILDREN_CACHE.put(cacheKey, children);
            }
            return children;
        }
    }

    public Collection<Resource> list()
    {
        Map<String, Resource> children = getChildren();
        return new ArrayList<Resource>(children.values());
    }

    public boolean exists()
    {
        return _dirs != null && !_dirs.isEmpty();
    }

    public boolean isCollection()
    {
        return exists() && _dirs.get(0).isDirectory();
    }

    public Resource find(String name)
    {
        Resource r = getChildren().get(name);
        if (r == null && AppProps.getInstance().isDevMode())
        {
            for (File dir : _dirs)
            {
                // might not be case sensitive, but this is just devmode
                File f = new File(dir,name);
                if (f.exists())
                    return new FileResource(_resolver, getPath().append(f.getName()), f);
            }
        }
        return r;
    }

    public Collection<String> listNames()
    {
        return new ArrayList<String>(getChildren().keySet());
    }

    public long getVersionStamp()
    {
        synchronized (_lock)
        {
            if (System.currentTimeMillis() > versionStampTime + minCacheTime)
            {
                long version = getLastModified();

                for (Resource r : list())
                    version += r.getVersionStamp();

                versionStamp = version;
            }

            return versionStamp;
        }
    }

    public long getLastModified()
    {
        return exists() ? _dirs.get(0).lastModified() : Long.MIN_VALUE;
    }

}
