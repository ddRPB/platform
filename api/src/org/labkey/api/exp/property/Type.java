/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.api.exp.property;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
* User: adam
* Date: Apr 29, 2009
* Time: 9:44:07 AM
*/
// TODO: Consider getting rid of this, adding the xsd: to PropertyType instead
public enum Type
{
    StringType("Text (String)", "xsd:string", "varchar", String.class),
    IntType("Integer", "xsd:int", "integer", Integer.class, Integer.TYPE, Short.class, Short.TYPE, Byte.class, Byte.TYPE),
    DoubleType("Number (Double)", "xsd:double", "double", Double.class, Double.TYPE, Float.class, Float.TYPE), // Double.TYPE is here because manually created datasets with required doubles return Double.TYPE as Class
    DateTimeType("DateTime", "xsd:dateTime", "timestamp", Date.class),
    BooleanType("Boolean", "xsd:boolean", "boolean", Boolean.class, Boolean.TYPE),
    AttachmentType("Attachment", "xsd:attachment", "varchar", String.class);

    private String label;
    private String xsd;
    private Class clazz;
    private Set<Class> allClasses = new HashSet<Class>();
    private String sqlTypeName;

    Type(String label, String xsd, String sqlTypeName, Class clazz, Class... additionalClasses)
    {
        this.label = label;
        this.xsd = xsd;
        this.clazz = clazz;
        this.allClasses.add(clazz);
        this.allClasses.addAll(Arrays.asList(additionalClasses));
        this.sqlTypeName = sqlTypeName;
    }

    public String getLabel()
    {
        return label;
    }

    public String getXsdType()
    {
        return xsd;
    }

    public Class getJavaClass()
    {
        return clazz;
    }

    public boolean matches(Class clazz)
    {
        return allClasses.contains(clazz);
    }

    public String getSqlTypeName()
    {
        return sqlTypeName;
    }

    public boolean isNumeric()
    {
        return this == IntType || this == DoubleType;
    }

    public static Type getTypeByLabel(String label)
    {
        for (Type type : values())
        {
            if (type.getLabel().equals(label))
                return type;
        }
        return null;
    }

    public static Type getTypeByXsdType(String xsd)
    {
        for (Type type : values())
        {
            if (type.getXsdType().equals(xsd))
                return type;
        }
        return null;
    }

    public static Type getTypeByClass(Class clazz)
    {
        for (Type type : values())
        {
            if (type.matches(clazz))
                return type;
        }
        return null;
    }

    public static Type getTypeBySqlTypeName(String sqlTypeName)
    {
        for (Type type : values())
        {
            if (type.getSqlTypeName().equals(sqlTypeName))
                return type;
        }
        return null;
    }
}
