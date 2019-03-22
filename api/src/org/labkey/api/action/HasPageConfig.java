/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.api.action;

import org.labkey.api.view.template.PageConfig;
import org.springframework.web.servlet.mvc.Controller;

/**
 * Indicates that a {@link org.springframework.web.servlet.mvc.Controller} wants to know about {@link PageConfig} contexts.
 * User: matthewb
 * Date: May 31, 2007
 */
public interface HasPageConfig extends Controller
{
    void setPageConfig(PageConfig page);
    PageConfig getPageConfig();
}
