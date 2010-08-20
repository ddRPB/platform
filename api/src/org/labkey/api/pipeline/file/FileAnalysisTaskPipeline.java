/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
package org.labkey.api.pipeline.file;

import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.util.FileType;
import org.labkey.api.util.URLHelper;
import org.labkey.api.data.Container;

import java.io.File;
import java.io.FileFilter;
import java.util.List;
import java.util.Map;

/**
 * <code>FileAnalysisTaskPipeline</code>
 */
public interface FileAnalysisTaskPipeline extends TaskPipeline<FileAnalysisTaskPipelineSettings>
{
    /**
     * Returns a description of this pipeline to be displayed in the
     * user interface for initiating a job that will run the pipeline.
     *
     * @return a description of this pipeline
     */
    String getDescription();

    /**
     * Returns the name of the protocol factory for this pipeline, which
     * will be used as the root directory name for all analyses of this type
     * and the directory name of the saved system protocol XML files.
     *
     * @return the name of the protocol factory
     */
    String getProtocolFactoryName();

    /**
     * Returns the full list of acceptable file types that can be used to
     * start this pipeline.
     *
     * @return list containing acceptable initial file types
     */
    List<FileType> getInitialFileTypes();

    /**
     * Returns a <code>FileFilter</code> for use in creating an input file set.
     *
     * @return filter for input files
     */
    FileFilter getInitialFileTypeFilter();

    URLHelper getAnalyzeURL(Container c, String path);

    public Map<FileType, FileType[]> getTypeHierarchy();
}
