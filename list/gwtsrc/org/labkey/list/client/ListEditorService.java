/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

package org.labkey.list.client;

import com.google.gwt.user.client.rpc.IsSerializable;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.LookupService;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 26, 2007
 * Time: 1:34:56 PM
 */
public interface ListEditorService extends LookupService
{
    public static class ListImportException extends Exception implements IsSerializable
    {
        public ListImportException()
        {
            
        }

        public ListImportException(String msg)
        {
            super(msg);
        }
    }

    public GWTList getList(int id);
    public GWTList createList(GWTList list) throws ListImportException;
    public List<String> getListNames(); // names in use already
    public static final int MAX_NAME_LENGTH = 64;
    public List<String> updateListDefinition(GWTList list, GWTDomain orig, GWTDomain dd) throws Exception;
    public GWTDomain getDomainDescriptor(GWTList list) throws Exception;
}
