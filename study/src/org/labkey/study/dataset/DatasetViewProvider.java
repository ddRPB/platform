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
package org.labkey.study.dataset;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.views.DataViewInfo;
import org.labkey.api.data.views.DataViewProvider;
import org.labkey.api.data.views.DefaultViewInfo;
import org.labkey.api.data.views.ProviderType;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Apr 2, 2012
 */
public class DatasetViewProvider implements DataViewProvider
{
    private static final DataViewProvider.Type _type = new ProviderType("datasets", "Provides a view of Study Datasets", true);

    public static DataViewProvider.Type getType()
    {
        return _type;
    }

    @Override
    public boolean isVisible(Container container, User user)
    {
        Study study = StudyService.get().getStudy(container);

        return study != null;
    }

    @Override
    public List<DataViewInfo> getViews(ViewContext context) throws Exception
    {
        List<DataViewInfo> datasets = new ArrayList<DataViewInfo>();
        Container container = context.getContainer();
        User user = context.getUser();

        if (isVisible(container, user))
        {
            Study study = StudyService.get().getStudy(container);
            for (DataSet ds : study.getDataSets())
            {
                if (ds.canRead(user))
                {
                    DefaultViewInfo view = new DefaultViewInfo(_type, ds.getEntityId(), ds.getLabel(), container);

                    if (ds.getCategory() != null)
                    {
                        view.setCategory(ViewCategoryManager.getInstance().getCategory(container, ds.getCategory()));
/*
                        view.setCategory(ds.getCategory());

                        ViewCategory vc = ViewCategoryManager.getInstance().getCategory(container, ds.getCategory());
                        if (vc != null)
                            view.setCategoryDisplayOrder(vc.getDisplayOrder());
                        else
                            view.setCategoryDisplayOrder(ReportUtil.DEFAULT_CATEGORY_DISPLAY_ORDER);
*/
                    }
                    else
                    {
                        view.setCategory(ReportUtil.getDefaultCategory(container, null, null));
/*
                        view.setCategory("Uncategorized");
                        view.setCategoryDisplayOrder(ReportUtil.DEFAULT_CATEGORY_DISPLAY_ORDER);
*/
                    }
                    view.setType("Dataset");
                    view.setDescription(ds.getDescription());
                    view.setIcon(AppProps.getInstance().getContextPath() + "/reports/grid.gif");
                    view.setVisible(ds.isShowByDefault());

                    ActionURL runUrl = new ActionURL(StudyController.DefaultDatasetReportAction.class, container).addParameter("datasetId", ds.getDataSetId());
                    view.setRunUrl(runUrl);
                    view.setDetailsUrl(runUrl);
                    
                    view.setThumbnailUrl(new ActionURL(StudyController.ThumbnailAction.class, container));
                    view.setModified(ds.getModified());

                    view.setTags(ReportPropsManager.get().getProperties(ds.getEntityId(), container));

                    datasets.add(view);
                }
            }
        }
        return datasets;
    }

    @Override
    public DataViewProvider.EditInfo getEditInfo()
    {
        return new EditInfoImpl();
    }

    public static class EditInfoImpl implements DataViewProvider.EditInfo
    {
        private static final String[] _editableProperties = {
                Property.description.name(),
                Property.category.name(),
                Property.visible.name(),
                Property.author.name(),
                Property.refreshDate.name(),
                Property.status.name(),
        };

        @Override
        public String[] getEditableProperties(Container container, User user)
        {
            return _editableProperties;
        }

        @Override
        public void validateProperties(Container container, User user, String id, Map<String, Object> props) throws ValidationException
        {
            StudyImpl study = StudyManager.getInstance().getStudy(container);
            List<ValidationError> errors = new ArrayList<ValidationError>();

            if (study != null)
            {
                DataSetDefinition dsDef = StudyManager.getInstance().getDataSetDefinitionByEntityId(study, id);
                if (dsDef == null)
                    errors.add(new SimpleValidationError("Unable to locate the dataset for the specified ID"));
            }
            else
                errors.add(new SimpleValidationError("No study defined for this folder"));

            if (!errors.isEmpty())
                throw new ValidationException(errors);
        }

        @Override
        public void updateProperties(Container container, User user, String id, Map<String, Object> props) throws Exception
        {
            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            try {
                scope.ensureTransaction();

                StudyImpl study = StudyManager.getInstance().getStudy(container);
                if (study != null)
                {
                    DataSetDefinition dsDef = StudyManager.getInstance().getDataSetDefinitionByEntityId(study, id);
                    if (dsDef != null)
                    {
                        ViewCategory category = null;

                        // save the category information then the dataset information
                        if (props.containsKey(Property.category.name()))
                        {
                            String categoryName = String.valueOf(props.get(Property.category.name()));
                            if (categoryName != null)
                                category = ViewCategoryManager.getInstance().ensureViewCategory(container, user, categoryName);
                        }

                        boolean dirty = false;
                        dsDef = dsDef.createMutable();
                        if (category != null)
                        {
                            dirty = dsDef.getCategoryId() == null || (category.getRowId() != dsDef.getCategoryId());
                            dsDef.setCategoryId(category.getRowId());
                        }

                        if (props.containsKey(Property.description.name()))
                        {
                            String newDescription = StringUtils.trimToNull(String.valueOf(props.get(Property.description.name())));
                            dirty = dirty || !StringUtils.equals(dsDef.getDescription(), newDescription);
                            dsDef.setDescription(newDescription);
                        }

                        if (props.containsKey(Property.visible.name()))
                        {
                            boolean visible = BooleanUtils.toBoolean(String.valueOf(props.get(Property.visible.name())));
                            dirty = dirty || (dsDef.isShowByDefault() != visible);
                            dsDef.setShowByDefault(visible);
                        }

                        if (dirty)
                            dsDef.save(user);

                        if (props.containsKey(Property.author.name()))
                            ReportPropsManager.get().setPropertyValue(id, container, Property.author.name(), props.get(Property.author.name()));
                        if (props.containsKey(Property.status.name()))
                            ReportPropsManager.get().setPropertyValue(id, container, Property.status.name(), props.get(Property.status.name()));
                        if (props.containsKey(Property.refreshDate.name()))
                            ReportPropsManager.get().setPropertyValue(id, container, Property.refreshDate.name(), props.get(Property.refreshDate.name()));

                        scope.commitTransaction();
                    }
                }
            }
            finally
            {
                scope.closeConnection();
            }
        }
    }
}
