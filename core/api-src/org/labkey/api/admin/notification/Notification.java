/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.admin.notification;

import org.labkey.api.util.GUID;

/**
 * User: cnathe
 * Date: 9/14/2015
 */
public class Notification
{
    private int _rowId;
    private GUID _container;
    private int _userId;
    private String _objectId;
    private String _type;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getUserId()
    {
        return _userId;
    }

    public void setUserId(int userId)
    {
        _userId = userId;
    }

    public String getObjectId()
    {
        return _objectId;
    }

    public void setObjectId(String objectId)
    {
        _objectId = objectId;
    }

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }

    public GUID getContainer()
    {
        return _container;
    }

    public void setContainer(GUID container)
    {
        _container = container;
    }
}
