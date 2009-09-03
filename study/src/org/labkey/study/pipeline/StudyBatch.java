/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.study.pipeline;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.study.controllers.StudyController;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;

/**
 * User: brittp
 * Date: Mar 14, 2006
 * Time: 5:09:13 PM
 */
public abstract class StudyBatch extends PipelineJob implements Serializable
{
    protected File _definitionFile;

    public StudyBatch(ViewBackgroundInfo info, File definitionFile) throws SQLException
    {
        super("Study", info);
        _definitionFile = definitionFile;
    }

    public ActionURL getStatusHref()
    {
        // where should this go???
        return new ActionURL(StudyController.OverviewAction.class, getInfo().getContainer());
    }

    public String getDescription()
    {
        return "Import files";
    }

    public void submit() throws IOException
    {
        File log = StudyPipeline.logForInputFile(_definitionFile);
        setLogFile(log);
        PipelineService.get().queueJob(this);
    }

    public File getDefinitionFile()
    {
        return _definitionFile;
    }
}
