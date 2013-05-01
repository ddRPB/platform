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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.Table;
import org.labkey.api.etl.CopyConfig;
import org.labkey.api.exp.pipeline.ExpGeneratorId;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipelineSettings;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Path;
import org.labkey.api.di.ScheduledPipelineJobContext;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.di.DataIntegrationDbSchema;
import org.labkey.di.steps.SimpleQueryTransformStep;
import org.labkey.di.steps.SimpleQueryTransformStepMeta;
import org.labkey.etl.xml.EtlDocument;
import org.labkey.etl.xml.EtlType;
import org.labkey.etl.xml.SchemaQueryType;
import org.labkey.etl.xml.TransformType;
import org.labkey.etl.xml.TransformsType;
import org.quartz.ScheduleBuilder;
import org.quartz.SimpleScheduleBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class BaseQueryTransformDescriptor implements ScheduledPipelineJobDescriptor<ScheduledPipelineJobContext>, Serializable
{
    private static final Logger LOG = Logger.getLogger(BaseQueryTransformDescriptor.class);

    /** How often to check if the definition has changed */
    private static final int UPDATE_CHECK_FREQUENCY = 2000;

    private transient Resource _resource;
    private Path _resourcePath;
    private long _lastUpdateCheck;
    private long _lastModified;

    private String _id;
    private String _name;
    private String _description;
    private String _moduleName;

    // steps
    private ArrayList<SimpleQueryTransformStepMeta> _stepMetaDatas = new ArrayList<SimpleQueryTransformStepMeta>();


    public BaseQueryTransformDescriptor(Resource resource, String moduleName) throws XmlException, IOException
    {
        _resource = resource;
        _resourcePath = resource.getPath();
        _moduleName = moduleName;
        _id = "{" + moduleName + "}/" + _resourcePath.toString();
        parse();
    }

    private void parse() throws IOException, XmlException
    {
        InputStream inputStream = null;
        try
        {
            Resource resource = ensureResource();
            inputStream = resource.getInputStream();
            if (inputStream == null)
            {
                throw new IOException("Unable to get InputStream from " + resource);
            }
            _lastModified = resource.getLastModified();

            XmlOptions options = new XmlOptions();
            options.setValidateStrict();
            EtlDocument document = EtlDocument.Factory.parse(inputStream, options);
            EtlType etlXML = document.getEtl();

            _name = etlXML.getName();
            _description = etlXML.getDescription();
            TransformsType transforms = etlXML.getTransforms();
            if (transforms != null)
            {
                TransformType[] transformTypes = transforms.getTransformArray();
                for (TransformType t : transformTypes)
                {
                    SimpleQueryTransformStepMeta meta = buildSimpleQueryTransformStepMeta(t);
                    _stepMetaDatas.add(meta);
                }
            }
        }
        finally
        {
            if (inputStream != null) { try { inputStream.close(); } catch (IOException ignored) {} }
        }

    }

    private SimpleQueryTransformStepMeta buildSimpleQueryTransformStepMeta(TransformType transformXML) throws XmlException
    {
        SimpleQueryTransformStepMeta meta = new SimpleQueryTransformStepMeta();

        if (null == transformXML.getId())
            throw new XmlException("Id attribute is required");
        meta.setId(transformXML.getId());

        if (null != transformXML.getDescription())
        {
            meta.setDescription(transformXML.getDescription());
        }

        String className = transformXML.getType();
        if (null != className)
        {
            try
            {
                Class taskClass = Class.forName(className);
                if (isValidTaskClass(taskClass))
                {
                    meta.setTaskClass(taskClass);
                }
                else
                {
                    className = null;
                }
            }
            catch (ClassNotFoundException e)
            {
                throw new XmlException("Invalid transform class specified");
            }
        }

        if (null == className)
        {
            throw new  XmlException("Invalid transform class specified");
        }

        SchemaQueryType source = transformXML.getSource();
        if (null != source)
        {
            meta.setSourceSchema(SchemaKey.fromString(source.getSchemaName()));
            meta.setSourceQuery(source.getQueryName());
            if (null != source.getTimestampColumnName())
                meta.setSourceTimestampColumnName(source.getTimestampColumnName());
            if (null != source.getSourceOption())
            {
                try
                {
                    meta.setSourceOptions(CopyConfig.SourceOptions.valueOf(source.getSourceOption()));
                }
                catch (IllegalArgumentException x)
                {
                    // TODO
                    throw x;
                }
            }
        }
        SchemaQueryType destination = transformXML.getDestination();
        if (null != destination)
        {
            meta.setTargetSchema(SchemaKey.fromString(destination.getSchemaName()));
            meta.setTargetQuery(destination.getQueryName());
            if (null != destination.getTargetOption())
            {
                try
                {
                    meta.setTargetOptions(CopyConfig.TargetOptions.valueOf(destination.getTargetOption()));
                }
                catch (IllegalArgumentException x)
                {
                    // TODO
                    throw x;
                }
            }
        }

        return meta;
    }

    private Resource ensureResource()
    {
        if (_resource == null)
        {
            _resource = ModuleLoader.getInstance().getResource(_resourcePath);
            if (_resource == null)
            {
                throw new IllegalStateException("Could not resolve resource for " + _resourcePath + ", perhaps the ETL descriptor is no longer available?");
            }
        }
        return _resource;
    }

    public String getName()
    {
        checkForUpdates();
        return _name;
    }

    public String getDescription()
    {
        checkForUpdates();
        return _description;
    }

    public String getModuleName()
    {
        return _moduleName;
    }

    public String getId()
    {
        return _id;
    }

    public int getVersion()
    {
        checkForUpdates();
        // TODO - add config for real version number
        return 1;
    }

    private void checkForUpdates()
    {
        long currentTime = System.currentTimeMillis();
        if (_lastUpdateCheck + UPDATE_CHECK_FREQUENCY < currentTime)
        {
            _lastUpdateCheck = currentTime;
            if (_lastModified != ensureResource().getLastModified())
            {
                // XML has changed, time to reload
                try
                {
                    parse();
                }
                catch (IOException e)
                {
                    LOG.warn("Unable to parse " + ensureResource(), e);
                }
                catch (XmlException e)
                {
                    LOG.warn("Unable to parse " + ensureResource(), e);
                }
            }
        }
    }

    @Override
    public String toString()
    {
        return "ETLDescriptor: " + _name + " (" + getScheduleDescription() + ", " + _stepMetaDatas.get(0).toString() + ")";
    }


    public ScheduleBuilder getScheduleBuilder()
    {
        return SimpleScheduleBuilder.simpleSchedule()
                              .withIntervalInMilliseconds(getInterval())
                              .repeatForever();
    }

    public long getInterval()
    {
        return TimeUnit.MINUTES.toMillis(1);
    }


    public String getScheduleDescription()
    {
        return DateUtil.formatDuration(getInterval());
    }


    @Override
    public Class getJobClass()
    {
        return TransformJobRunner.class;
    }

    @Override
    public TransformJobContext getJobContext(Container c, User user)
    {
        return new TransformJobContext(this, c, user);
    }

    @Override
    public Callable<Boolean> getChecker(ScheduledPipelineJobContext context)
    {
        return new UpdatedRowsChecker(this, context, _stepMetaDatas);
    }


    @Override
    public PipelineJob getPipelineJob(ScheduledPipelineJobContext context) throws PipelineJobException
    {
        TransformJob job = new TransformJob((TransformJobContext)context, this);
        try
        {
            registerTransformSteps();
            PipelineService.get().setStatus(job, PipelineJob.WAITING_STATUS, null, true);
        }
        catch (Exception e)
        {
            LOG.error("Unable to queue ETL job", e);
            return null;
        }

        TransformRun run = new TransformRun();
        run.setStartTime(new Date());
        run.setTransformId(getId());
        run.setTransformVersion(getVersion());
        run.setContainer(context.getContainer());

        PipelineStatusFile statusFile = PipelineService.get().getStatusFile(job.getLogFile());
        run.setJobId(statusFile.getRowId());

        try
        {
            run = Table.insert(context.getUser(), DataIntegrationDbSchema.getTransformRunTableInfo(), run);
        }
        catch (SQLException e)
        {
            throw new PipelineJobException(e);
        }

        job.setRunId(run.getRowId());
        return job;
    }

    // for now we are using the BaseQueryTransformDescriptor as a catch-all for every ETL transform task
    PipelineJob.Task createTask(TestTaskFactory factory, TransformJob job, TransformJobContext context, int i)
    {
        SimpleQueryTransformStepMeta meta = getTransformStepMetaFromTaskId(factory.getId());

        if (null != meta)
            return new TestTask(factory, job, meta, context);

        return null;
    }

    PipelineJob.Task createTask(TransformTaskFactory factory, TransformJob job, TransformJobContext context, int i)
    {
        if (i != 0)
            throw new IllegalArgumentException();

        SimpleQueryTransformStepMeta meta = getTransformStepMetaFromTaskId(factory.getId());

        if (null != meta)
            return new SimpleQueryTransformStep(factory, job, meta, context);

        return null;

//        Class c = meta.getTargetStepClass();
//        try
//        {
//            PipelineJob.Task task = (PipelineJob.Task)c.getConstructor(meta.getClass()).newInstance(meta);
//            return task;
//        }
//        catch (NoSuchMethodException|InstantiationException|IllegalAccessException|InvocationTargetException|ClassCastException x)
//        {
//            throw new RuntimeException(x);
//        }
    }


    public Map<String, Object> toJSON(@Nullable Map<String,Object> map)
    {
        if (null == map)
            map = new JSONObject();
        map.put("id", getId());
        map.put("description", getDescription());
        map.put("name", getName());
        map.put("moduleName", getModuleName());
        map.put("scheduleDescription", getScheduleDescription());
        map.put("version", getVersion());
        return map;
    }

    private SimpleQueryTransformStepMeta getTransformStepMetaFromTaskId(TaskId tid)
    {
        SimpleQueryTransformStepMeta meta = null;

        // step ids are guaranteed to be unique
        for (int i = 0; i < _stepMetaDatas.size(); i++)
        {
            meta = _stepMetaDatas.get(i);
            if (StringUtils.equals(meta.getId(), tid.getName()))
                return meta;
        }

        return null;
    }

    public void registerTransformSteps() throws CloneNotSupportedException
    {
        ArrayList<Object> progressionSpec = new ArrayList<Object>();
        TaskPipelineSettings settings = new TaskPipelineSettings(org.labkey.di.pipeline.TransformJob.class);

        // Register all the tasks that are associated with this transform and
        // associate the correct stepMetaData with the task via the index
        for (int i = 0; i < _stepMetaDatas.size(); i++)
        {
            SimpleQueryTransformStepMeta meta = _stepMetaDatas.get(i);
            String taskName = meta.getId();
            Class taskClass = meta.getTaskClass();
            // check to see if this class is part of our known transform tasks
            if (org.labkey.di.pipeline.TransformTask.class.isAssignableFrom(taskClass))
            {
                PipelineJobService.get().addTaskFactory(new TransformTaskFactory(taskClass, taskName));
            }
            else
            if (org.labkey.di.pipeline.TestTask.class.isAssignableFrom(taskClass))
            {
                PipelineJobService.get().addTaskFactory(new TestTaskFactory(taskClass, taskName));
            }
            else
            {
                // we should have already checked this when parsing the ETL config file
                assert false;
                continue;
            }

            progressionSpec.add(new TaskId(taskClass, taskName));
        }

        // Register the task to generate an experiment run to track this transform as the last step.
        // The ExpGenerator factory should have already been registered by the Experiment module
        progressionSpec.add(new TaskId(ExpGeneratorId.class));

        // add the pipeline
        settings.setTaskProgressionSpec(progressionSpec.toArray());
        PipelineJobService.get().addTaskPipeline(settings);
    }

    private boolean isValidTaskClass(Class taskClass)
    {
        if (org.labkey.di.pipeline.TransformTask.class.isAssignableFrom(taskClass) ||
            org.labkey.di.pipeline.TestTask.class.isAssignableFrom(taskClass))
            return true;

        return false;
    }
}
