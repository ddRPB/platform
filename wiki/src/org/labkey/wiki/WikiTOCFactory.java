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

import java.lang.reflect.InvocationTargetException;

/**
 * User: adam
 * Date: Nov 5, 2008
 * Time: 10:51:27 AM
 */
public class WikiTOCFactory extends BaseWebPartFactory
{
    public WikiTOCFactory()
    {
        super("Wiki TOC", "right", true, false);
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
    {
        WebPartView v = new WikiController.WikiTOC(portalCtx);
        //TODO: Should just use setters
        populateProperties(v, webPart.getPropertyMap());
        return v;
    }

    @Override
    public HttpView getEditView(Portal.WebPart webPart)
    {
        return new JspView<Portal.WebPart>("/org/labkey/wiki/view/customizeWikiToc.jsp", webPart);
    }
}
