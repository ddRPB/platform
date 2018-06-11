/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.core.admin.usageMetrics;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.usageMetrics.UsageMetricsProvider;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.MinorConfigurationException;
import org.labkey.api.util.UsageReportingLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Tony on 2/14/2017.
 */
public class UsageMetricsServiceImpl implements UsageMetricsService
{
    private final Map<UsageReportingLevel, Map<String, UsageMetricsProvider>> moduleUsageReports = new ConcurrentHashMap<>();

    @Override
    public void registerUsageMetrics(UsageReportingLevel level, String moduleName, UsageMetricsProvider metrics)
    {
        if (UsageReportingLevel.NONE == level)
            throw new MinorConfigurationException(moduleName + " module registered metric for UsageReportingLevel NONE. This will never be sent.");
        moduleUsageReports.computeIfAbsent(level, k -> new ConcurrentHashMap<>()).put(moduleName, metrics);
    }

    @Override
    @Nullable
    public Map<String, Map<String, Object>> getModuleUsageMetrics(UsageReportingLevel level)
    {
        if (null == moduleUsageReports.get(level))
            return null;
        else
        {
            Map<String, Map<String, Object>> allModulesMetrics = new HashMap<>();
            moduleUsageReports.get(level).forEach((moduleName, provider) ->
            {
                try
                {
                    allModulesMetrics.put(moduleName, provider.getUsageMetrics());
                }
                catch (Exception e)
                {
                    allModulesMetrics.computeIfAbsent(ERRORS, k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)).put(moduleName, e.getMessage());
                }
            });

            return allModulesMetrics;
        }
    }
}
