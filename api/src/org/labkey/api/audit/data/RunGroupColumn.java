/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.audit.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;

/**
 * User: klum
 * Date: Mar 15, 2012
 */
public class RunGroupColumn extends ExperimentAuditColumn
{
    public RunGroupColumn(ColumnInfo col, ColumnInfo containerId, @Nullable ColumnInfo defaultName)
    {
        super(col, containerId, defaultName);
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object rowId = getBoundColumn().getValue(ctx);
        String cId = (String)ctx.get("ContainerId");
        if (rowId != null && cId != null)
        {
            Container c = ContainerManager.getForId(cId);
            if (c != null)
            {
                ExpExperiment runGroup = ExperimentService.get().getExpExperiment((Integer)rowId);

                ActionURL url = null;
                if (runGroup != null)
                    url = PageFlowUtil.urlProvider(ExperimentUrls.class).getExperimentDetailsURL(c, runGroup);

                if (url != null)
                {
                    out.write("<a href=\"" + url.getLocalURIString() + "\">" + PageFlowUtil.filter(runGroup.getName()) + "</a>");
                }
            }
        }
    }
}
