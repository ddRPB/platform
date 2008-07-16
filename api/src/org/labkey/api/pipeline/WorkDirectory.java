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
package org.labkey.api.pipeline;

import org.labkey.api.util.FileType;

import java.io.File;
import java.io.IOException;

/**
 * <code>WorkDirectory</code>
 *
 * @author brendanx
 */
public interface WorkDirectory
{
    enum Function { input, output }

    static final String DATA_REL_PATH = "../..";

    /**
     * @return the directory where the input files live and where the output files will end up
     */
    File getDir();

    File newFile(String name);

    File newFile(Function f, String name);

    File newFile(FileType type);

    File newFile(Function f, FileType type);

    File inputFile(File fileInput, boolean forceCopy) throws IOException;

    String getRelativePath(File fileWork) throws IOException;

    void outputFile(File fileWork) throws IOException;

    void outputFile(File fileWork, String nameDest) throws IOException;

    /**
     * Delete a file
     * @param fileWork
     * @throws IOException
     */
    void discardFile(File fileWork) throws IOException;

    void remove() throws IOException;

    /**
     * Ensures that we have a lock, if needed. The lock must be released by the caller.
     */
    public CopyingResource ensureCopyingLock() throws IOException;

    public interface CopyingResource
    {
        public void release();
    }
}
