/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

package org.labkey.api.webdav;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Oct 21, 2008
 * Time: 10:45:23 AM
 */
public class WebdavService
{
    static WebdavResolver _resolver = null;
    static CopyOnWriteArrayList<Provider> _providers = new CopyOnWriteArrayList<Provider>();




    // this is the resolver used to resolve http requests
    public static void setResolver(WebdavResolver r)
    {
        _resolver = r;
    }
    
    public static WebdavResolver getResolver()
    {
        return _resolver;
    }

    public static String getServletPath()
    {
        return "_webdav";
    }



    /*
     * interface for resources that are accessible through http:
     */

    
    public interface Provider
    {
        // currently addChildren is called only for web folders
        @Nullable
        Set<String> addChildren(@NotNull Resource target);
        Resource resolve(@NotNull Resource parent, @NotNull String name);
    }

    public static void addProvider(Provider provider)
    {
        _providers.add(provider);
    }

    public static List<Provider> getProviders()
    {
        return _providers;
    }
}
