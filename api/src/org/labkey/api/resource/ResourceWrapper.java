package org.labkey.api.resource;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Path;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * Created by adam on 10/16/2016.
 */
public class ResourceWrapper implements Resource
{
    private final Resource _resource;

    public ResourceWrapper(Resource resource)
    {
        _resource = resource;
    }

    protected Resource getWrappedResource()
    {
        return _resource;
    }

    @Override
    public Resolver getResolver()
    {
        return _resource.getResolver();
    }

    @Override
    public Path getPath()
    {
        return _resource.getPath();
    }

    @Override
    public String getName()
    {
        return _resource.getName();
    }

    @Override
    public boolean exists()
    {
        return _resource.exists();
    }

    @Override
    public boolean isCollection()
    {
        return _resource.isCollection();
    }

    @Override
    public Resource find(String name)
    {
        return _resource.find(name);
    }

    @Override
    public boolean isFile()
    {
        return _resource.isFile();
    }

    @Override
    public Collection<String> listNames()
    {
        return _resource.listNames();
    }

    @Override
    public Collection<? extends Resource> list()
    {
        return _resource.list();
    }

    @Override
    public Resource parent()
    {
        return _resource.parent();
    }

    @Override
    public long getVersionStamp()
    {
        return _resource.getVersionStamp();
    }

    @Override
    public long getLastModified()
    {
        return _resource.getLastModified();
    }

    @Override
    @Nullable
    public InputStream getInputStream() throws IOException
    {
        return _resource.getInputStream();
    }

    @Override
    public String toString()
    {
        return _resource.toString();
    }
}
