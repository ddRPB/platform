/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.di.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.di.ScheduledPipelineJobContext;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;


/**
 * User: matthewb
 * Date: 2013-03-18
 * Time: 4:05 PM
 */
public class TransformQuartzJobRunner implements Job
{
    private static final Logger LOG = Logger.getLogger(TransformQuartzJobRunner.class);


    public TransformQuartzJobRunner()
    {
    }


    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException
    {
        ScheduledPipelineJobDescriptor d = getDescriptorFromJobDetail(context);
        ScheduledPipelineJobContext infoTemplate = ScheduledPipelineJobContext.getFromQuartzJobDetail(context);
        ScheduledPipelineJobContext info = infoTemplate.clone();

        boolean hasWork = d.checkForWork(info, true, info.isVerbose());
        if (!hasWork)
            return;

        try
        {
            PipelineJob job = d.getPipelineJob(info);
            if (null == job)
                return;

            try
            {
                PipelineService.get().setStatus(job, PipelineJob.WAITING_STATUS, null, true);
                PipelineService.get().queueJob(job);
            }
            catch (Exception e)
            {
                LOG.error("Unable to queue ETL job", e);
            }
        }
        catch (RuntimeException x)
        {
            throw x;
        }
        catch (Exception x)
        {
            throw new UnexpectedException(x);
        }
    }


    public static ScheduledPipelineJobDescriptor getDescriptorFromJobDetail(JobExecutionContext jobExecutionContext)
    {
        JobDataMap map = jobExecutionContext.getJobDetail().getJobDataMap();
        Object result = map.get(ScheduledPipelineJobDescriptor.class.getName());
        if (result == null)
        {
            map = jobExecutionContext.getJobDetail().getJobDataMap();
            result = map.get(ScheduledPipelineJobDescriptor.class.getName());
            if (result == null)
                throw new IllegalArgumentException("No ScheduledPipelineJobDescriptor found!");
        }
        return (ScheduledPipelineJobDescriptor) result;
    }
}
