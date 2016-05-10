package org.labkey.api.analytics;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.QuerySettings;

public abstract class QueryAnalyticsProvider extends AnalyticsProvider
{
    public abstract boolean isApplicable(@NotNull QuerySettings settings);
}
