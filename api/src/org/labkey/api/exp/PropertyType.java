/*
 * Copyright (c) 2005-2011 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.labkey.api.data.JdbcType;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.ExcelFactory;

import java.io.File;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * User: migra
 * Date: Oct 25, 2005
 * Time: 2:10:08 PM
 */
public enum PropertyType
{
    BOOLEAN("http://www.w3.org/2001/XMLSchema#boolean", "Boolean", 'f', JdbcType.BOOLEAN, 10, null, Cell.CELL_TYPE_BOOLEAN, Boolean.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getBooleanCellValue();
        }

        @Override
        public Object convert(Object value) throws ConversionException
        {
            boolean boolValue = false;
            if (value instanceof Boolean)
                boolValue = (Boolean)value;
            else if (null != value && !"".equals(value))
                boolValue = (Boolean) ConvertUtils.convert(value.toString(), Boolean.class);
            return boolValue;
        }
    },
    STRING("http://www.w3.org/2001/XMLSchema#string", "String", 's', JdbcType.VARCHAR, 100, null, Cell.CELL_TYPE_STRING, String.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getStringCellValue();
        }
        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (value instanceof String)
                return value;
            else
                return ConvertUtils.convert(value);
        }
    },
    MULTI_LINE("http://www.w3.org/2001/XMLSchema#multiLine", "MultiLine", 's', JdbcType.VARCHAR, 1000, "textarea", Cell.CELL_TYPE_STRING, String.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getStringCellValue();
        }
        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (value instanceof String)
                return value;
            else
                return ConvertUtils.convert(value);
        }
    },
    RESOURCE("http://www.w3.org/2000/01/rdf-schema#Resource", "PropertyURI", 's', JdbcType.VARCHAR, 100, null, Cell.CELL_TYPE_STRING, Identifiable.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getStringCellValue();
        }
        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (null == value)
                return null;
            if (value instanceof Identifiable)
                return ((Identifiable) value).getLSID();
            else
                return value.toString();
        }
    },
    INTEGER("http://www.w3.org/2001/XMLSchema#int", "Integer", 'f', JdbcType.INTEGER, 10, null, Cell.CELL_TYPE_NUMERIC, Integer.class, Long.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return (int)cell.getNumericCellValue();
        }
        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (null == value)
                return null;
            if (value instanceof Integer)
                return value;
            else
                return ConvertUtils.convert(value.toString(), Integer.class);
        }
    },
    /** Stored as a path to a file on the server's file system */
    FILE_LINK("http://cpas.fhcrc.org/exp/xml#fileLink", "FileLink", 's', JdbcType.VARCHAR, 100, "file", Cell.CELL_TYPE_STRING, File.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getStringCellValue();
        }
        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (null == value)
                return null;
            if (value instanceof File)
                return ((File) value).getPath();
            else
                return String.valueOf(value);
        }
    },
    /** Stored in the database as a BLOB using AttachmentService */
    ATTACHMENT("http://www.labkey.org/exp/xml#attachment", "Attachment", 's', JdbcType.VARCHAR, 100, "file", Cell.CELL_TYPE_STRING, File.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getStringCellValue();
        }
        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (null == value)
                return null;
            if (value instanceof File)
                return ((File) value).getPath();
            else
                return String.valueOf(value);
        }
    },
    DATE_TIME("http://www.w3.org/2001/XMLSchema#dateTime", "DateTime", 'd', JdbcType.TIMESTAMP, 100, null, Cell.CELL_TYPE_NUMERIC, Date.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            Date date = cell.getDateCellValue();
            if (date != null)
            {
                DateFormat format = new SimpleDateFormat("MM/dd/yyyy GG HH:mm:ss.SSS");
                format.setTimeZone(TimeZone.getDefault());
                String s = format.format(date);
                try
                {
                    date = format.parse(s);
                }
                catch (ParseException e)
                {
                    throw new ConversionException(e);
                }
//                int offset = TimeZone.getDefault().getOffset(date.getTime());
//                date.setTime(date.getTime() - offset);
            }
            return date;
        }
        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (null == value)
                return null;
            if (value instanceof Date)
                return value;
            else
                return ConvertUtils.convert(value.toString(), Date.class);
        }
    },
    DOUBLE("http://www.w3.org/2001/XMLSchema#double", "Double", 'f', JdbcType.DOUBLE, 20, null, Cell.CELL_TYPE_NUMERIC, Double.class, Float.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getNumericCellValue();
        }
        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (null == value)
                return null;
            if (value instanceof Double)
                return value;
            else
                return ConvertUtils.convert(String.valueOf(value), Double.class);
        }
    },
    XML_TEXT("http://cpas.fhcrc.org/exp/xml#text-xml", "XmlText", 's', JdbcType.LONGVARCHAR, 100, null, Cell.CELL_TYPE_STRING, null)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getStringCellValue();
        }
        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (value instanceof String)
                return value;
            else
                return ConvertUtils.convert(value);
        }
    };

    private String typeURI;
    private String xarName;
    private char storageType;
    private int excelCellType;
    private JdbcType jdbcType;
    private int scale;
    private String inputType;
    private Class javaType;
    private Class[] additionalTypes;

    private static Map<String, PropertyType> uriToProperty = null;
    private static Map<String, PropertyType> xarToProperty = null;

    PropertyType(String typeURI,
                 String xarName,
                 char storageType,
                 JdbcType jdbcType,
                 int scale,
                 String inputType,
                 int excelCellType,
                 Class javaType,
                 Class... additionalTypes)
    {
        this.typeURI = typeURI;
        this.xarName = xarName;
        this.storageType = storageType;
        this.jdbcType = jdbcType;
        this.scale = scale;
        this.inputType = inputType;
        this.javaType = javaType;
        this.excelCellType = excelCellType;
        this.additionalTypes = additionalTypes;
    }

    public String getTypeUri()
    {
        return typeURI;
    }

    public String getXmlName()
    {
        return xarName;
    }

    public char getStorageType()
    {
        return storageType;
    }

    public int getSqlType()
    {
        return jdbcType.sqlType;
    }
    
    public JdbcType getJdbcType()
    {
        return jdbcType;
    }

    public int getScale()
    {
        return scale;
    }

    public String getInputType()
    {
        return inputType;
    }

    public Class getJavaType()
    {
        return javaType;
    }

    public String getXarName()
    {
        return xarName;
    }

    public static PropertyType getFromURI(String concept, String datatype)
    {
        return getFromURI(concept, datatype, RESOURCE);
    }


    static
    {
        Map<String, PropertyType> m = new HashMap<String, PropertyType>();

        for (PropertyType t : values())
        {
            String uri = t.getTypeUri();
            m.put(uri, t);
            m.put(t.getXmlName(), t);

            if (uri.startsWith("http://www.w3.org/2001/XMLSchema#") || uri.startsWith("http://www.labkey.org/exp/xml#"))
            {
                String xsdName = uri.substring(uri.indexOf('#') + 1);
                m.put("xsd:" + xsdName, t);
                m.put(xsdName, t);
            }
        }

        uriToProperty = m;
    }

    public static PropertyType getFromURI(String concept, String datatype, PropertyType def)
    {
        PropertyType p = uriToProperty.get(concept);

        if (null == p)
        {
            p = uriToProperty.get(datatype);
            if (null == p)
                p = def;
        }

        return p;
    }


    public static PropertyType getFromXarName(String xarName)
    {
        if (null == xarToProperty)
        {
            Map<String, PropertyType> m = new HashMap<String, PropertyType>();
            for (PropertyType t : values())
            {
                m.put(t.getXmlName(), t);
            }
            xarToProperty = m;
        }

        PropertyType p = xarToProperty.get(xarName);

        return null == p ? RESOURCE : p;
    }


    public static PropertyType getFromClass(Class clazz)
    {
        if (clazz == BigDecimal.class)
            clazz = Double.class;

        for (PropertyType t : values())
        {
            if (t.javaType == null)
                continue;
            if (t.javaType.isAssignableFrom(clazz))
                return t;
        }

        // after trying the primary types, we then try any additional types:
        for (PropertyType t : values())
        {
            if (t.additionalTypes == null || t.additionalTypes.length == 0)
                continue;
            for (Class type : t.additionalTypes)
            {
                if (type.isAssignableFrom(clazz))
                    return t;
            }
        }
        throw new IllegalArgumentException("No such class mapping: " + clazz.getName());
    }

    protected abstract Object convertExcelValue(Cell cell) throws ConversionException;

    public abstract Object convert(Object value) throws ConversionException;

    public static Object getFromExcelCell(Cell cell) throws ConversionException
    {
        if (Cell.CELL_TYPE_NUMERIC == cell.getCellType())
        {
            // Ugly, the POI implementation doesn't expose an explicit date type
            if (DateUtil.isCellDateFormatted(cell))
                return DATE_TIME.convertExcelValue(cell);
            else
                // special handling for the "number type": prefer double.
                // Without this, we'd default to integer
                return DOUBLE.convertExcelValue(cell);
        }

        for (PropertyType t : values())
        {
            if (t.excelCellType == cell.getCellType())
                return t.convertExcelValue(cell);
        }
        return ExcelFactory.getCellStringValue(cell);
    }
}
