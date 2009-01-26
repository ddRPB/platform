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
package org.labkey.api.module;

import org.labkey.api.action.SpringActionController;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.WebPartView;
import org.labkey.api.data.Container;
import org.labkey.api.util.Cache;
import org.springframework.web.servlet.mvc.Controller;

import java.util.Collection;
import java.util.Collections;
import java.io.File;
import java.io.IOException;

/*
* User: Dave
* Date: Jan 23, 2009
* Time: 10:16:37 AM
*/

/**
 * Provides a simple Spring controller implementation that resolves action
 * names to html files in the module's views/ directory.
 */
public class SimpleController extends SpringActionController implements SpringActionController.ActionResolver
{
    public static final String VIEWS_DIRECTORY = "views";
    public static final String BEGIN_VIEW_NAME = "begin";

    public SimpleController()
    {
        setActionResolver(this);
    }

    public Controller resolveActionName(Controller actionController, String actionName)
    {
        String controllerName = getViewContext().getActionURL().getPageFlow();
        Module module = ModuleLoader.getInstance().getModule(controllerName);
        if(null == module)
            return null;

        File viewFile = new File(new File(module.getExplodedPath(), VIEWS_DIRECTORY), actionName + ModuleHtmlView.HTML_VIEW_EXTENSION);
        if(viewFile.exists() && viewFile.isFile())
            return new SimpleAction(viewFile);
        else
            return null;
    }

    public void addTime(Controller action, long elapsedTime)
    {
    }

    public Collection<ActionDescriptor> getActionDescriptors()
    {
        return Collections.emptyList();
    }

    public static ActionURL getBeginViewUrl(Module module, Container container)
    {
        File beginViewFile = new File(new File(module.getExplodedPath(), VIEWS_DIRECTORY), BEGIN_VIEW_NAME + ModuleHtmlView.HTML_VIEW_EXTENSION);
        if(beginViewFile.exists() && beginViewFile.isFile())
            return new ActionURL(module.getName(), BEGIN_VIEW_NAME, container);
        else
            return null;
    }

}