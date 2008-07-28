/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.view;

import org.labkey.api.settings.AppProps;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class StaticContentCachingFilter implements Filter
{
    private static boolean _cachingAllowed = AppProps.getInstance().isCachingAllowed();

    public void destroy()
    {
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
    {
        HttpServletRequest request = (HttpServletRequest)servletRequest;
        String path = null != request.getServletPath() ? request.getServletPath().toLowerCase() : "";
        boolean cachableJsFile = path.contains("/ext-")
                                || path.contains("/_yui/")
                                || path.contains("/tiny_mce/");

        if (cachableJsFile || _cachingAllowed)
        {
            HttpServletResponse response = (HttpServletResponse)servletResponse;
            response.setDateHeader("Expires", System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 5);
            response.setHeader("Cache-Control", "private");
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    public void init(FilterConfig filterConfig) throws ServletException
    {
    }
}
