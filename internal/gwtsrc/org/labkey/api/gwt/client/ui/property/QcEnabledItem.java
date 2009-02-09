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
package org.labkey.api.gwt.client.ui.property;

import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.PropertyPane;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlexTable;

/**
 * User: jgarms
 * Date: Jan 7, 2009
 */
public class QcEnabledItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends CheckboxItem<DomainType, FieldType>
{
    private final RequiredItem requiredItem;

    public QcEnabledItem(PropertyPane<DomainType, FieldType> propertyPane, RequiredItem requiredItem)
    {
        super(propertyPane);
        this.requiredItem = requiredItem;
        checkbox.setName("allowsQc");
    }

    protected String getCheckboxLabelText()
    {
        return "Allows QC";
    }

    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        // Called when clicked or keyed

        if (checkbox.isEnabled())
        {
            if (!getFieldValue(field) == checkbox.isChecked())
            {
                if (requiredItem.isChecked())
                {
                    // TODO: A required item can't have QC. This needs to pop a dialog or disable itself
                }
                setFieldValue(field, checkbox.isChecked());
                return true;
            }
            // No change
            return false;
        }
        return false;
    }

    protected boolean getFieldValue(FieldType field)
    {
        return field.isQcEnabled();
    }

    protected void setFieldValue(FieldType field, boolean b)
    {
        field.setQcEnabled(b);
    }

}