/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.pipeline;

/*
* User: dave
* Date: Sep 24, 2009
* Time: 4:05:12 PM
*/

/**
 * Represents a warning raised by a pipeline job/task
 */
public class PipelineJobWarning
{
    private String _message;
    private Throwable _t;

    public PipelineJobWarning(String message)
    {
        this(message, null);
    }

    public PipelineJobWarning(String message, Throwable t)
    {
        _message = message;
        _t = t;
    }

    public String getMessage()
    {
        return _message;
    }

    public Throwable getThrowable()
    {
        return _t;
    }

    @Override
    public String toString()
    {
        return getMessage();
    }
}
