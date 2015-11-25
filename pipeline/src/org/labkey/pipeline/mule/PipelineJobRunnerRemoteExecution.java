/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
package org.labkey.pipeline.mule;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.util.JobRunner;
import org.labkey.pipeline.api.PipelineJobServiceImpl;
import org.labkey.pipeline.api.PipelineStatusFileImpl;
import org.labkey.pipeline.api.PipelineStatusManager;
import org.labkey.pipeline.mule.filters.TaskJmsSelectorFilter;
import org.mule.umo.UMODescriptor;
import org.mule.umo.UMOEventContext;
import org.mule.umo.endpoint.UMOEndpoint;
import org.mule.umo.lifecycle.Callable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PipelineJobRunnerRemoteExecution implements Callable, ResumableDescriptor
{
    private static Logger _log = Logger.getLogger(PipelineJobRunnerRemoteExecution.class);

    public PipelineJobRunnerRemoteExecution()
    {
        // Note: can't throw exception at config time, for missing
        //       client information, since it must be possible to run the web
        //       server without remote execution configuration.
    }

    public void resume(UMODescriptor descriptor)
    {
        for (UMOEndpoint endpoint : (List<UMOEndpoint>)descriptor.getInboundRouter().getEndpoints())
        {
            if (endpoint.getFilter() instanceof TaskJmsSelectorFilter)
            {
                TaskJmsSelectorFilter filter = (TaskJmsSelectorFilter) endpoint.getFilter();
                final Map<String, List<PipelineStatusFileImpl>> allLocations = new HashMap<>();
                Map<String, RemoteExecutionEngine> configuredLocations = new CaseInsensitiveHashMap<>();
                for (PipelineJobService.RemoteExecutionEngineConfig config : PipelineJobService.get().getRemoteExecutionEngineConfigs())
                {
                    configuredLocations.put(config.getLocation(), PipelineJobServiceImpl.get().getRemoteExecutionEngine(config.getType()));
                }
                for (String location : filter.getLocations())
                {
                    if (configuredLocations.containsKey(location))
                    {
                        // Grab the list of jobs to check synchronously, but don't block waiting to ping them all
                        allLocations.put(location, PipelineStatusManager.getStatusFilesForLocation(location, false));
                    }
                }
                JobRunner.getDefault().execute(new Runnable()
                {
                    public void run()
                    {
                        for (Map.Entry<String, List<PipelineStatusFileImpl>> entry : allLocations.entrySet())
                        {
                            String location = entry.getKey();
                            List<PipelineStatusFileImpl> filesToCheck = entry.getValue();

                            _log.info("Starting to check status for " + filesToCheck.size() + " jobs on remote location '" + location + "'");
                            int count = 0;
                            for (PipelineStatusFileImpl sf : filesToCheck)
                            {
                                if (sf.getJobStore() != null && sf.isActive())
                                {
                                    PipelineJob job = null;
                                    try
                                    {
                                        job = PipelineJobService.get().getJobStore().fromXML(sf.getJobStore());

                                        RemoteExecutionEngine engine = getEngine(job);

                                        // Refresh to see what state the engine thinks the job is in
                                        String status = engine.getStatus(job.getJobGUID());
                                        sf.setStatus(status);
                                        PipelineStatusManager.updateStatusFile(sf);
                                    }
                                    catch (Exception e)
                                    {
                                        if (job != null)
                                        {
                                            job.error("Failed to update status", e);
                                        }
                                    }
                                }
                                count++;
                                if (count % 10 == 0)
                                {
                                    _log.info("Checked status for " + count + " of " + filesToCheck.size() + " jobs on remote location '" + location + "'");
                                }
                            }
                            _log.info("Finished checking status jobs on remote location '" + location + "'");
                        }
                        _log.info("Finished checking status jobs for all remote locations");
                    }
                });
            }
        }
    }

    @NotNull
    private RemoteExecutionEngine getEngine(PipelineJob job) throws ClassNotFoundException
    {
        TaskFactory taskFactory = PipelineJobService.get().getTaskFactory(job.getActiveTaskId());
        if (taskFactory == null)
        {
            throw new IllegalStateException("Could not get taskFactory for job " + job.getJobGUID());
        }

        for (PipelineJobService.RemoteExecutionEngineConfig config : PipelineJobServiceImpl.get().getRemoteExecutionEngineConfigs())
        {
            if (config.getLocation().equalsIgnoreCase(taskFactory.getExecutionLocation()))
            {
                return PipelineJobServiceImpl.get().getRemoteExecutionEngine(config.getType());
            }
        }

        throw new IllegalStateException("Could not find execution engine for location " + taskFactory.getExecutionLocation() + " for job " + job.getJobGUID());
    }

    public Object onCall(UMOEventContext eventContext) throws Exception
    {
        boolean submitted = false;
        String xmlJob = eventContext.getMessageAsString();
        PipelineJob job = PipelineJobService.get().getJobStore().fromXML(xmlJob);

        PipelineStatusFileImpl statusFile = PipelineStatusManager.getStatusFile(job.getLogFile());
        if (statusFile == null)
        {
            job.error("Could not find job in database");
            return null;
        }
        if (PipelineJob.TaskStatus.cancelling.matches(statusFile.getStatus()))
        {
            job.info("Job has been cancelled, aborting remote submit");
            statusFile.setStatus(PipelineJob.TaskStatus.cancelled.toString());
            PipelineStatusManager.updateStatusFile(statusFile);
            return null;
        }
        if (PipelineJob.TaskStatus.cancelled.matches(statusFile.getStatus()))
        {
            job.info("Job has been cancelled, aborting remote submit");
            return null;
        }

        try
        {
            RemoteExecutionEngine engine = getEngine(job);
            engine.submitJob(job);

            _log.info("Job " + job.getJobGUID() + " submitted to remote engine " + engine.getType());
            submitted = true;
        }
        finally
        {
            if (!submitted)
            {
                job.error("Unable to submit job");
            }
        }
        return null;
    }
}