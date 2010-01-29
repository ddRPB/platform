/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.api.exp;

import org.labkey.api.data.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.query.*;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;

/**
 * User: jeckels
 * Date: Oct 12, 2006
 */
public class ExperimentRunListView extends QueryView
{
    private boolean _showAddToExperimentButton = false;
    private boolean _showRemoveFromExperimentButton = false;
    private boolean _showMoveRunsButton = false;

    private final ExperimentRunType _selectedType;

    public ExperimentRunListView(UserSchema schema, QuerySettings settings, ExperimentRunType selectedType)
    {
        super(schema, settings);
        _buttonBarPosition = DataRegion.ButtonBarPosition.BOTTOM;
        _selectedType = selectedType;
        setShowDetailsColumn(false);
        setShowExportButtons(true);
        setShowRecordSelectors(true);
    }

    public static QuerySettings getRunListQuerySettings(UserSchema schema, ViewContext model, String tableName, boolean allowCustomizations)
    {
        QuerySettings settings = new QuerySettings(model, tableName);
        settings.setSchemaName(schema.getSchemaName());
        settings.getQueryDef(schema);
        settings.setAllowChooseQuery(false);
        settings.setAllowChooseView(allowCustomizations);
        settings.setQueryName(tableName);
        return settings;
    }

    public static ExperimentRunListView createView(ViewContext model, ExperimentRunType selectedType, boolean allowCustomizations)
    {
        UserSchema schema = QueryService.get().getUserSchema(model.getUser(), model.getContainer(), selectedType.getSchemaName());
        return new ExperimentRunListView(schema, getRunListQuerySettings(schema, model, selectedType.getTableName(), allowCustomizations), selectedType);
    }


    public DataView createDataView()
    {
        DataView result = super.createDataView();
        result.getRenderContext().setBaseSort(new Sort("-RowId"));
        return result;
    }

    public void setShowAddToRunGroupButton(boolean showAddToExperimentButton)
    {
        _showAddToExperimentButton = showAddToExperimentButton;
    }
    
    public void setShowMoveRunsButton(boolean showMoveRunsButton)
    {
        _showMoveRunsButton = showMoveRunsButton;
    }

    public void setShowRemoveFromExperimentButton(boolean showRemoveFromExperimentButton)
    {
        _showRemoveFromExperimentButton = showRemoveFromExperimentButton;
    }

    private ExpExperiment getExperiment()
    {
        return getRunTable().getExperiment();
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        // Can't use view.getViewContext(), since it's not rendering
        // should probably pass in ViewContext
        ViewContext context = HttpView.currentContext();
        Container c = context.getContainer();

        if (_showRemoveFromExperimentButton)
        {
            getExperiment();
            ActionURL removeRunUrl = PageFlowUtil.urlProvider(ExperimentUrls.class).getRemoveSelectedExpRunsURL(getContainer(), getReturnURL(), getExperiment());
            ActionButton removeRunAction = new ActionButton("","Remove");
            removeRunAction.setURL(removeRunUrl);
            removeRunAction.setActionType(ActionButton.Action.POST);
            removeRunAction.setRequiresSelection(true);

            removeRunAction.setDisplayPermission(DeletePermission.class);
            bar.add(removeRunAction);
        }

        if (showDeleteButton())
        {
            ActionButton deleteButton = new ActionButton("button", "Delete");
            ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getDeleteSelectedExpRunsURL(context.getContainer(), getReturnURL());
            deleteButton.setURL(url);
            deleteButton.setActionType(ActionButton.Action.POST);
            deleteButton.setRequiresSelection(true);
            deleteButton.setDisplayPermission(DeletePermission.class);
            bar.add(deleteButton);
        }

        if (_showAddToExperimentButton && c.hasPermission(context.getUser(), InsertPermission.class))
        {
            MenuButton addToExperimentButton = new MenuButton("Add to run group");
            addToExperimentButton.setRequiresSelection(true);

            ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getCreateRunGroupURL(getViewContext().getContainer(), getReturnURL(), true);
            String javascript = "javascript: " + view.getDataRegion().getJavascriptFormReference(false) + ".method = \"POST\";\n " +
                    view.getDataRegion().getJavascriptFormReference(false) + ".action = " + PageFlowUtil.jsString(url + "&noPost=true") + ";\n " +
                    view.getDataRegion().getJavascriptFormReference(false) + ".submit();";
            addToExperimentButton.addMenuItem("Create new run group...", javascript);

            ExpExperiment[] experiments = ExperimentService.get().getExperiments(c, getViewContext().getUser(), true, false);
            if (experiments.length > 0)
            {
                addToExperimentButton.addSeparator();
            }

            for (ExpExperiment exp : experiments)
            {
                ActionURL addRunUrl = PageFlowUtil.urlProvider(ExperimentUrls.class).getAddRunsToExperimentURL(getContainer(), exp);
                addToExperimentButton.addMenuItem(exp.getName(), null, "if (verifySelected(" + view.getDataRegion().getJavascriptFormReference(false) + ", \"" + addRunUrl.getLocalURIString() + "\", \"post\", \"run\")) { " + view.getDataRegion().getJavascriptFormReference(false) + ".submit(); }");
            }
            bar.add(addToExperimentButton);
        }

        if (_showMoveRunsButton)
        {
            ActionButton deleteButton = new ActionButton("button", "Move");
            ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getMoveRunsLocationURL(getContainer());
            deleteButton.setURL(url);
            deleteButton.setActionType(ActionButton.Action.POST);
            deleteButton.setRequiresSelection(true);
            deleteButton.setDisplayPermission(DeletePermission.class);
            bar.add(deleteButton);
        }

        _selectedType.populateButtonBar(context, bar, view, getTable().getContainerFilter());
    }

    @Override
    public PanelButton createExportButton(boolean exportAsWebPage)
    {
        PanelButton result = super.createExportButton(exportAsWebPage);
        String defaultFilenamePrefix = "Exported " + (getTitle() == null ? "Runs" : getTitle());

        HttpView filesView = ExperimentService.get().createFileExportView(getContainer(), defaultFilenamePrefix);
        result.addSubPanel("Files", filesView);

        HttpView xarView = ExperimentService.get().createRunExportView(getContainer(), defaultFilenamePrefix);
        result.addSubPanel("XAR", xarView);

        return result;
    }

    protected DataRegion createDataRegion()
    {
        DataRegion result = super.createDataRegion();
        result.setShadeAlternatingRows(true);
        result.setShowBorders(true);
        result.setName(_selectedType.getTableName());
        for (DisplayColumn column : result.getDisplayColumns())
        {
            if (column.getCaption().startsWith("Experiment Run "))
            {
                column.setCaption(column.getCaption().substring("Experiment Run ".length()));
            }
        }
        result.setRecordSelectorValueColumns("RowId");
        if (getRunTable().getExperiment() != null)
        {
            result.addHiddenFormField("expLSID", getRunTable().getExperiment().getLSID());
        }
        return result;
    }

    protected ActionURL urlFor(QueryAction action)
    {
        switch (action)
        {
            case deleteQueryRows:
                return null;
        }
        return super.urlFor(action);
    }

    public ExpRunTable getRunTable()
    {
        return (ExpRunTable)getTable();
    }
}
