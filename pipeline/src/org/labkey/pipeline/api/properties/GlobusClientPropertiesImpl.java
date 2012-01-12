/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.pipeline.api.properties;

import org.labkey.api.pipeline.GlobusSettings;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.GlobusSettingsImpl;
import org.labkey.api.pipeline.file.PathMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * <code>GlobusClientPropertiesImpl</code> used for Spring configuration.
 */
public class GlobusClientPropertiesImpl extends GlobusSettingsImpl implements PipelineJobService.GlobusClientProperties
{
    private String _javaHome; // todo: make this unnecessary
    private String _labKeyDir;
    private String _globusServer;
    private String _globusEndpoint;
    private String _jobFactoryType;
    private PathMapper _pathMapper;
    private Set<String> _availableQueues = new LinkedHashSet<String>();

    public String getJavaHome()
    {
        return _javaHome;
    }

    public void setJavaHome(String globusJavaHome)
    {
        _javaHome = globusJavaHome;
    }

    public String getLabKeyDir()
    {
        return _labKeyDir;
    }

    public void setLabKeyDir(String globusLabkeyDir)
    {
        _labKeyDir = globusLabkeyDir;
    }

    public String getGlobusServer()
    {
        return _globusServer;
    }

    public void setGlobusServer(String globusServer)
    {
        _globusServer = globusServer;

        _globusEndpoint = globusServer;
        if (_globusEndpoint != null)
        {
            if (!"http".equalsIgnoreCase(_globusEndpoint.substring(0, 4)))
                _globusEndpoint = "https://" + _globusEndpoint;
            if (_globusEndpoint.lastIndexOf('/') < 8)
            {
                _globusEndpoint += "/wsrf/services/ManagedJobFactoryService";
            }
        }
    }

    public String getGlobusEndpoint()
    {
        return _globusEndpoint;
    }

    public String getJobFactoryType()
    {
        return _jobFactoryType;
    }

    public void setJobFactoryType(String globusJobFactoryType)
    {
        _jobFactoryType = globusJobFactoryType;
    }

    public PathMapper getPathMapper()
    {
        return _pathMapper;
    }

    public void setPathMapper(PathMapper pathMapper)
    {
        _pathMapper = pathMapper;
    }

    public List<String> getAvailableQueues()
    {
        return new ArrayList<String>(_availableQueues);
    }

    public void setAvailableQueues(List<String> availableQueues)
    {
        _availableQueues.addAll(availableQueues);
    }

    @Override
    public String getQueue()
    {
        return _availableQueues.isEmpty() ? null : _availableQueues.iterator().next();
    }

    @Override
    public void setQueue(String queue)
    {
        _availableQueues.add(queue);
    }

    @Override
    public GlobusClientPropertiesImpl mergeOverrides(GlobusSettings overrides)
    {
        GlobusClientPropertiesImpl result = (GlobusClientPropertiesImpl) super.mergeOverrides(overrides);
        if (overrides instanceof GlobusClientPropertiesImpl)
        {
            GlobusClientPropertiesImpl overrides2 = (GlobusClientPropertiesImpl)overrides;
            result.setGlobusServer(overrides2.getGlobusServer() == null ? getGlobusServer() : overrides2.getGlobusServer());
            result.setJavaHome(overrides2.getJavaHome() == null ? getJavaHome() : overrides2.getJavaHome());
            result.setLabKeyDir(overrides2.getLabKeyDir() == null ? getLabKeyDir() : overrides2.getLabKeyDir());
            result.setJobFactoryType(overrides2.getJobFactoryType() == null ? getJobFactoryType() : overrides2.getJobFactoryType());
            result.setPathMapper(overrides2.getPathMapper() == null ? getPathMapper() : overrides2.getPathMapper());
            result.setAvailableQueues(overrides2.getAvailableQueues() == null ? getAvailableQueues() : overrides2.getAvailableQueues());
        }
        else
        {
            result.setGlobusServer(getGlobusServer());
            result.setJavaHome(getJavaHome());
            result.setLabKeyDir(getLabKeyDir());
            result.setJobFactoryType(getJobFactoryType());
            result.setPathMapper(getPathMapper());
            result.setAvailableQueues(getAvailableQueues());
        }
        return result;
    }

    @Override
    protected GlobusClientPropertiesImpl createMergeResult()
    {
        return new GlobusClientPropertiesImpl();
    }
}