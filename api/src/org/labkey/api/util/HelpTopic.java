/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.api.util;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.annotations.JavaRuntimeVersion;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.NavTree;

import java.util.Formatter;
import java.util.Map;

/**
 * User: Tamra Myers
 * Date: Feb 15, 2007
 * Time: 1:10:09 PM
 */
public class HelpTopic
{
    public static String TARGET_NAME = "labkeyHelp"; // LabKey help should always appear in the same tab/window
    private static String HELP_VERSION = null;

    @JavaRuntimeVersion // Update this link whenever we require a new major Java version so we always point at the current docs
    private static final String JDK_JAVADOC_BASE_URL = "http://docs.oracle.com/javase/7/docs/api/";

    private String _topic;

    public static String getHelpVersion()
    {
        if (HELP_VERSION == null)
        {
            // Get core module version number, truncate to one decimal place, and use as help version
            Module core = ModuleLoader.getInstance().getCoreModule();
            double coreVersion = core.getVersion();
            HELP_VERSION = Formats.f1.format(Math.floor(coreVersion * 10) / 10);
        }
        return HELP_VERSION;
    }

    public static final HelpTopic DEFAULT_HELP_TOPIC = new HelpTopic("default");

    public HelpTopic(@NotNull String topic)
    {
        if (topic == null)
            throw new IllegalArgumentException("Topic cannot be null");
        
        _topic = topic;
    }

    @Override
    public String toString()
    {
        return getHelpTopicHref();
    }

    public String getHelpTopicHref()
    {
        return "http://help.labkey.org/wiki/home/documentation/" + getHelpVersion() + "/page.view?name=" + _topic;
    }

    // Create a simple link (just an <a> tag with plain mixed case text, no graphics) that links to the help topic, displays
    // the provided text, uses the standard target, etc. Use in cases where LabKey standard link style doesn't fit in.
    public String getSimpleLinkHtml(String displayText)
    {
        StringBuilder html = new StringBuilder();
        html.append("<a href=\"");
        html.append(PageFlowUtil.filter(getHelpTopicHref()));
        html.append("\" target=\"");
        html.append(TARGET_NAME);
        html.append("\">");
        html.append(PageFlowUtil.filter(displayText));
        html.append("</a>");

        return html.toString();
    }

    private static final Map<String, String> TARGET_MAP = PageFlowUtil.map("target", TARGET_NAME);

    // TODO: Use this in place where it makes sense (search results page, etc.)
    // Create a standard LabKey style link (all caps + arrow right) to the help topic, displaying the provided text, using the standard target, etc.
    public String getLinkHtml(String displayText)
    {
        return PageFlowUtil.textLink(displayText, getHelpTopicHref(), null, null, TARGET_MAP);
    }

    // Get create a NavTree for a menu item that to the help topic, displays the provided text, uses the standard target, etc.
    public NavTree getNavTree(String displayText)
    {
        NavTree tree = new NavTree(displayText, getHelpTopicHref());
        tree.setTarget(HelpTopic.TARGET_NAME);

        return tree;
    }

    /**
     * @return a link to the Oracle JDK JavaDocs for whatever the current LabKey-supported JDK is
     */
    public static String getJDKJavaDocLink(Class c)
    {
        return JDK_JAVADOC_BASE_URL + c.getName().replace(".", "/").replace("$", ".") + ".html";
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testJavaDocLinkGeneration()
        {
            assertEquals(JDK_JAVADOC_BASE_URL + "java/util/Formatter.html", getJDKJavaDocLink(Formatter.class));
            assertEquals(JDK_JAVADOC_BASE_URL + "java/util/Map.Entry.html", getJDKJavaDocLink(Map.Entry.class));
        }
    }
}
