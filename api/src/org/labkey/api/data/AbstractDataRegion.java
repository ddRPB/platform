/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.DisplayElement;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: Jun 23, 2011
 * Time: 7:20:16 PM
 */
public abstract class AbstractDataRegion extends DisplayElement
{
    private static final Logger _log = Logger.getLogger(DataRegion.class);
    private String _name = null;
    private QuerySettings _settings = null;
    protected boolean _allowHeaderLock = true; // Set to 'true' to enable header locking.
    private final String _domId = "lk-region-" + UniqueID.getServerSessionScopedUID(); // TODO: Consider using UniqueID.getRequestScopedUID(request) instead

    public enum PaginationLocation
    {
        TOP,
        BOTTOM,
    }

    public enum MessagePart
    {
        view,
        filter,
        header,
    }

    public final String getDomId()
    {
        return _domId;
    }

    public final String getFormId()
    {
        return getDomId() + "-form";
    }

    public String getName()
    {
        if (null == _name)
        {
            if (null != getSettings() && null != getSettings().getDataRegionName())
                _name = getSettings().getDataRegionName();
            else if (getTable() != null)
                _name = getTable().getName();
        }
        return _name;
    }

    public TableInfo getTable()
    {
        return null;
    }

    /**
     * Use {@link DataRegion#setSettings(QuerySettings)} to set the name instead.
     */
    @Deprecated
    public void setName(String name)
    {
        _name = name;
    }

    public void setSettings(QuerySettings settings)
    {
        _settings = settings;
    }

    public QuerySettings getSettings()
    {
        return _settings;
    }

    public void setAllowHeaderLock(boolean allow)
    {
        _allowHeaderLock = allow;
    }

    public boolean getAllowHeaderLock()
    {
        return _allowHeaderLock && !PageFlowUtil.useExperimentalCoreUI();
    }

    protected JSONObject toJSON(RenderContext ctx)
    {
        JSONObject dataRegionJSON = new JSONObject();
        dataRegionJSON.put("domId", getDomId());
        dataRegionJSON.put("name", getName());

        if (getSettings() != null)
        {
            dataRegionJSON.put("schemaName", getSettings().getSchemaName());
            dataRegionJSON.put("queryName", getSettings().getQueryName());
            dataRegionJSON.put("viewName", getSettings().getViewName());
            dataRegionJSON.put("containerFilter", getSettings().getContainerFilterName());
        }

        dataRegionJSON.put("allowHeaderLock", getAllowHeaderLock());
        return dataRegionJSON;
    }

    protected void renderHeaderScript(RenderContext ctx, Writer writer, Map<String, String> messages, boolean showRecordSelectors) throws IOException
    {
        JSONObject dataRegionJSON = toJSON(ctx);

        if (messages != null && !messages.isEmpty())
        {
            dataRegionJSON.put("messages", messages);
        }

        StringWriter out = new StringWriter();
        out.write("<script type=\"text/javascript\">\n");
        out.write("LABKEY.DataRegion.create(");
        out.write(dataRegionJSON.toString(2));
        out.write(");\n");
        out.write("</script>\n");
        writer.write(out.toString());
    }

    @Nullable
    protected SimpleFilter getValidFilter(RenderContext ctx)
    {
        SimpleFilter urlFilter = new SimpleFilter(ctx.getViewContext().getActionURL(), getName());
        for (FieldKey fk : ctx.getIgnoredFilterColumns())
            urlFilter.deleteConditions(fk);
        if (urlFilter.getClauses().isEmpty())
            return null;
        return urlFilter;
    }

    private static final String[] HIDDEN_FILTER_COLUMN_SUFFIXES = {"RowId", "DisplayName", "Description", "Label", "Caption", "Value"};

    @Nullable
    protected String getFilterDescription(RenderContext ctx) throws IOException
    {
        SimpleFilter urlFilter = getValidFilter(ctx);

        if (urlFilter != null && urlFilter.displayFilterText())
        {
            return urlFilter.getFilterText(new SimpleFilter.ColumnNameFormatter()
            {
                @Override
                public String format(FieldKey fieldKey)
                {
                    String formatted = super.format(fieldKey);
                    for (String hiddenFilter : HIDDEN_FILTER_COLUMN_SUFFIXES)
                    {
                        if (formatted.toLowerCase().endsWith("/" + hiddenFilter.toLowerCase()) ||
                                formatted.toLowerCase().endsWith("." + hiddenFilter.toLowerCase()))
                        {
                            formatted = formatted.substring(0, formatted.length() - (hiddenFilter.length() + 1));
                        }
                    }
                    int dotIndex = formatted.lastIndexOf('.');
                    if (dotIndex >= 0)
                        formatted = formatted.substring(dotIndex + 1);
                    int slashIndex = formatted.lastIndexOf('/');
                    if (slashIndex >= 0)
                        formatted = formatted.substring(slashIndex);
                    return formatted;
                }
            });
        }

        return null;
    }

    protected String getFilterErrorMessage(RenderContext ctx) throws IOException
    {
        StringBuilder buf = new StringBuilder();
        Set<FieldKey> ignoredColumns = ctx.getIgnoredFilterColumns();
        if (!ignoredColumns.isEmpty())
        {
            if (ignoredColumns.size() == 1)
            {
                FieldKey field = ignoredColumns.iterator().next();
                buf.append("Ignoring filter/sort on column '").append(field.toDisplayString()).append("' because it does not exist.");
            }
            else
            {
                String comma = "";
                buf.append("Ignoring filter/sort on columns ");
                for (FieldKey field : ignoredColumns)
                {
                    buf.append(comma);
                    comma = ", ";
                    buf.append("'");
                    buf.append(field.toDisplayString());
                    buf.append("'");
                }
                buf.append(" because they do not exist.");
            }
        }
        return buf.toString();
    }


    protected void addHeaderMessage(StringBuilder headerMessage, RenderContext ctx) throws IOException
    {
    }


    protected void addViewMessage(StringBuilder headerMessage, RenderContext ctx) throws IOException
    {
        headerMessage.append("<span class='labkey-strong'>View:</span>&nbsp;");
        headerMessage.append("<span style='padding:5px 45px 5px 0;'>");
        if (isDefaultView(ctx))
            headerMessage.append("default");
        else
            headerMessage.append(PageFlowUtil.filter(ctx.getView().getLabel()));

        if (ctx.getView() != null && ctx.getView().isLoadedFromTableTitle())
        {
            HelpTopic topic = new HelpTopic("title-loaded-view");
            headerMessage.append("&nbsp;<span class='labkey-error'>").append(CustomView.TITLE_BOUND_CUSTOM_VIEW_WARNING_HELPLINK).append("</span>&nbsp;");
            headerMessage.append(topic.getLinkHtml("help"));

            _log.error("Custom View: '" + ctx.getView().getLabel() + "' in folder : " + ctx.getContainer().getPath() + " is being loaded by matching it's table title.");
        }
        headerMessage.append("</span>&nbsp;");
    }

    protected boolean isDefaultView(RenderContext ctx)
    {
        return (ctx.getView() == null || StringUtils.isEmpty(ctx.getView().getName()));
    }

    public @NotNull Map<String, Object> getQueryParameters()
    {
        return null == getSettings() ? Collections.emptyMap() : getSettings().getQueryParameters();
    }

    /**
     * Adds any filter error messages and optionally the filter description that is applied to the current context.
     *
     * @param headerMessage         The StringBuilder to append messages to
     * @param showFilterDescription Specifies whether the filter description should be added
     */
    protected void addFilterMessage(StringBuilder headerMessage, RenderContext ctx, boolean showFilterDescription) throws IOException
    {
        String filterErrorMsg = getFilterErrorMessage(ctx);
        String filterDescription = showFilterDescription ? getFilterDescription(ctx) : null;
        if (filterErrorMsg != null && filterErrorMsg.length() > 0)
            headerMessage.append("<span class=\"labkey-error\">").append(PageFlowUtil.filter(filterErrorMsg)).append("</span>");

        String sectionSeparator = "";

        Map<String, Object> parameters = getQueryParameters();
        if (!parameters.isEmpty())
        {
            headerMessage.append("<span class='labkey-strong'>Parameters:</span>&nbsp;");
            String separator = "";
            for (Map.Entry<String, Object> entry : parameters.entrySet())
            {
                headerMessage.append(separator);
                separator = ", ";
                headerMessage.append(PageFlowUtil.filter(entry.getKey()));
                headerMessage.append("&nbsp;=&nbsp;");
                headerMessage.append(PageFlowUtil.filter(entry.getValue()));
            }
            headerMessage.append("&nbsp;&nbsp;").append(PageFlowUtil.button("Clear All").href("#")
                    .onClick("LABKEY.DataRegions[" + PageFlowUtil.jsString(getName()) + "].clearAllParameters(); return false;"));
            sectionSeparator = "<br/><br/>";
        }

        if (filterDescription != null)
        {
            headerMessage.append(sectionSeparator);
            headerMessage.append("<span class='labkey-strong'>Filter:</span>&nbsp;");
            headerMessage.append(PageFlowUtil.filter(filterDescription)).append("&nbsp;&nbsp;");
            headerMessage.append(PageFlowUtil.button("Clear All").href("#")
                    .onClick("LABKEY.DataRegions[" + PageFlowUtil.jsString(getName()) + "].clearAllFilters(); return false;"));
        }
    }

    protected abstract boolean shouldRenderHeader(boolean renderButtons);

    protected abstract void renderButtons(RenderContext ctx, Writer out) throws IOException;

    protected abstract void renderPagination(RenderContext ctx, Writer out, PaginationLocation location) throws IOException;

    protected void renderHeader(RenderContext ctx, Writer out, boolean renderButtons, int colCount) throws IOException
    {
        out.write("\n<tr");
        if (!shouldRenderHeader(renderButtons))
            out.write(" style=\"display:none\"");
        out.write(" id=\"" + PageFlowUtil.filter(getDomId() + "-header-row") + "\">");

        out.write("<td colspan=\"");
        out.write(String.valueOf(colCount));
        out.write("\" class=\"labkey-data-region-header-container\">\n");

        out.write("<table class=\"labkey-data-region-header\" cellpadding=\"0\" cellspacing=\"0\" id=\"" + PageFlowUtil.filter(getDomId() + "-header") + "\">\n");
        out.write("<tr><td nowrap>\n");
        if (renderButtons)
        {
            renderButtons(ctx, out);
        }
        out.write("</td>");

        out.write("<td align=\"right\" valign=\"top\" nowrap>\n");
        renderPagination(ctx, out, PaginationLocation.TOP);
        out.write("</td></tr>\n");

        renderRibbon(ctx, out);
        renderMessageBox(ctx, out, colCount);

        // end table.labkey-data-region-header
        out.write("</table>\n");

        out.write("\n</td></tr>");

        if (this.getAllowHeaderLock())
        {
            out.write("\n<tr");
            if (!shouldRenderHeader(renderButtons))
                out.write(" style=\"display:none\"");
            out.write(" id=\"" + PageFlowUtil.filter(getDomId() + "-header-row-spacer") + "\" style=\"display: none;\">");

            out.write("<td colspan=\"");
            out.write(String.valueOf(colCount));
            out.write("\" class=\"labkey-data-region-header-container\">\n");

            out.write("<table class=\"labkey-data-region-header\">\n");
            out.write("<tr><td nowrap>\n");
            out.write("</td>");

            out.write("<td align=\"right\" valign=\"top\" nowrap>\n");
            renderPagination(ctx, out, PaginationLocation.TOP);
            out.write("</td></tr>\n");

            renderRibbon(ctx, out);
            renderMessageBox(ctx, out, colCount);

            // end table.labkey-data-region-header
            out.write("</table>\n");

            out.write("\n</td></tr>");
        }
    }

    protected void renderRibbon(RenderContext ctx, Writer out) throws IOException
    {
        out.write("<tr>");
        out.write("<td colspan=\"2\" class=\"labkey-ribbon\" style=\"display:none;\"></td>");
        out.write("</tr>\n");
    }

    protected void renderMessageBox(RenderContext ctx, Writer out, int colCount) throws IOException
    {
        out.write("<tr id=\"" + PageFlowUtil.filter(getDomId() + "-msgbox") + "\" style=\"display:none\">");
        out.write("<td colspan=\"2\" class=\"labkey-dataregion-msgbox\">");
        out.write("<span class=\"labkey-dataregion-msg-toggle fa fa-minus\" "
                + "onclick=\"LABKEY.DataRegions[" + PageFlowUtil.filterQuote(getName()) + "].toggleMessageArea();\" "
                + "title=\"Collapse message\" alt=\"close\"></span>");
        out.write("<div></div>");
        out.write("</td>");
        out.write("</tr>\n");
    }
}
