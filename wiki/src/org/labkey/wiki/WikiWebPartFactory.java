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
package org.labkey.wiki;

import org.labkey.api.view.*;
import org.labkey.wiki.model.WikiWebPart;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * User: adam
 * Date: Nov 5, 2008
 * Time: 10:50:37 AM
 */
public class WikiWebPartFactory extends AlwaysAvailableWebPartFactory
{
    public WikiWebPartFactory()
    {
        this(WikiModule.WEB_PART_NAME, null);
    }

    public WikiWebPartFactory(String name, String location)
    {
        super(name, location, true, false);
        addLegacyNames("Narrow Wiki");
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
    {
        Map<String, String> props = webPart.getPropertyMap();
        return new WikiWebPart(webPart.getPageId(), webPart.getIndex(), props);
    }

    @Override
    public HttpView getEditView(Portal.WebPart webPart)
    {
        return new WikiController.CustomizeWikiPartView(webPart);
    }
}
