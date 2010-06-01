/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
package org.labkey.api.reader;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.MvUtil;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.util.CloseableIterator;
import org.labkey.api.util.Filter;
import org.labkey.api.util.FilterIterator;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * Abstract class for loading columnar data from different sources: TSVs, Excel files, etc.
 *
 * User: jgarms
 * Date: Oct 22, 2008
 * Time: 11:26:37 AM
 */
public abstract class DataLoader<T> implements Iterable<T>
{
    private static final Logger _log = Logger.getLogger(DataLoader.class);

    /**
     * Defines order of column type preferences. 
     * We'll try each one in turn, falling back
     * to the more general as necessary
     **/
    private final static Class[] CONVERT_CLASSES = new Class[]
    {
        Date.class,
        Integer.class,
        Double.class,
        Boolean.class,
        String.class
    };

    protected File _file = new File("Resource");

    protected ColumnDescriptor[] _columns;
    private boolean _initialized = false;
    protected int _scanAheadLineCount = 100; // number of lines to scan trying to infer data types
    // CONSIDER: explicit flags for hasHeaders, inferHeaders, skipLines etc.
    protected int _skipLines = -1;      // -1 means infer headers
    protected boolean _throwOnErrors = false;
    private Container _mvIndicatorContainer;

    protected DataLoader()
    {
    }

    protected DataLoader(Container mvIndicatorContainer)
    {
        _mvIndicatorContainer = mvIndicatorContainer;
    }

    public static DataLoader<Map<String, Object>> getDataLoaderForFile(File file) throws ServletException, IOException
    {
        return getDataLoaderForFile(file, null);
    }

    public static DataLoader<Map<String, Object>> getDataLoaderForFile(File file, Container mvIndicatorContainer) throws ServletException, IOException
    {
        String filename = file.getName();

        if (filename.endsWith("xls"))
        {
            return new ExcelLoader(file, true, mvIndicatorContainer);
        }
        else if (filename.endsWith("txt") || filename.endsWith("tsv"))
        {
            return new TabLoader(file, true, mvIndicatorContainer);
        }
        else if (filename.endsWith("csv"))
        {
            TabLoader loader = new TabLoader(file, true, mvIndicatorContainer);
            loader.parseAsCSV();
            return loader;
        }

        throw new ServletException("Unknown file type. File must have a suffix of .xls, .txt, .tsv or .csv.");
    }

    public boolean isThrowOnErrors()
    {
        return _throwOnErrors;
    }

    public void setThrowOnErrors(boolean throwOnErrors)
    {
        _throwOnErrors = throwOnErrors;
    }

    public final ColumnDescriptor[] getColumns() throws IOException
    {
        ensureInitialized();

        return _columns;
    }

    protected void ensureInitialized() throws IOException
    {
        if (!_initialized)
        {
            initialize();
            _initialized = true;
        }
    }

    protected void initialize() throws IOException
    {
        initializeColumns();
    }

    public void setColumns(ColumnDescriptor[] columns)
    {
        _columns = columns;
    }

    public void ensureColumn(ColumnDescriptor column) throws IOException
    {
        ColumnDescriptor[] existingColumns = getColumns();
        for (ColumnDescriptor existing : existingColumns)
        {
            if (existing.name.equalsIgnoreCase(column.name))
                return;
        }
        ColumnDescriptor[] newColumns = new ColumnDescriptor[existingColumns.length + 1];
        System.arraycopy(existingColumns, 0, newColumns, 0, existingColumns.length);
        newColumns[newColumns.length - 1] = column;
        setColumns(newColumns);
    }

    protected void initializeColumns() throws IOException
    {
        //Take our best guess since some columns won't map
        if (null == _columns)
            inferColumnInfo();
    }

    protected void setHasColumnHeaders(boolean hasColumnHeaders)
    {
        _skipLines = hasColumnHeaders ? 1 : 0;
    }

    protected void setSource(File inputFile) throws IOException
    {
        _file = inputFile;
        if (!_file.exists())
            throw new FileNotFoundException(_file.getPath());
        if (!_file.canRead())
            throw new IOException("Can't read file: " + _file.getPath());
    }

    /**
     * Return the data for the first n lines. Note that
     * subclasses are allowed to return fewer than n lines
     * if there are fewer rows than that in the data.
     **/
    public abstract String[][] getFirstNLines(int n) throws IOException;

    /**
     * Look at first <code>scanAheadLineCount</code> lines of the file and infer col names, data types.
     * Most useful if maps are being returned, otherwise use inferColumnInfo(reader, clazz) to
     * use properties of a bean instead.
     *
     * @throws java.io.IOException
     */
    @SuppressWarnings({"ConstantConditions"})
    private void inferColumnInfo() throws IOException
    {
        int numLines = _scanAheadLineCount + Math.max(_skipLines, 0);
        String[][] lineFields = getFirstNLines(numLines);
        numLines = lineFields.length;

        if (numLines == 0)
        {
            _columns = new ColumnDescriptor[0];
            return;
        }

        Set<String> missingValueIndicators = _mvIndicatorContainer != null ? MvUtil.getMvIndicators(_mvIndicatorContainer) : Collections.<String>emptySet();

        int nCols = 0;
        for (String[] lineField : lineFields)
        {
            nCols = Math.max(nCols, lineField.length);
        }

        ColumnDescriptor[] colDescs = new ColumnDescriptor[nCols];
        for (int i = 0; i < nCols; i++)
            colDescs[i] = new ColumnDescriptor();

        //Try to infer types
        int inferStartLine = _skipLines == -1 ? 1 : _skipLines;
        for (int f = 0; f < nCols; f++)
        {
            int classIndex = -1;
            for (int line = inferStartLine; line < numLines; line++)
            {
                if (f >= lineFields[line].length)
                    continue;
                String field = lineFields[line][f];
                if (missingValueIndicators.contains(field))
                {
                    colDescs[f].setMvEnabled(_mvIndicatorContainer);
                    continue;
                }

                if ("".equals(field))
                    continue;

                for (int c = Math.max(classIndex, 0); c < CONVERT_CLASSES.length; c++)
                {
                    //noinspection EmptyCatchBlock
                    try
                    {
                        Object o = ConvertUtils.convert(field, CONVERT_CLASSES[c]);
                        //We found a type that works. If it is more general than
                        //what we had before, we must use it.
                        if (o != null && c > classIndex)
                            classIndex = c;
                        break;
                    }
                    catch (Exception x)
                    {
                    }
                }
            }
            colDescs[f].clazz = classIndex == -1 ? String.class : CONVERT_CLASSES[classIndex];
        }

        //If first line is compatible type for all fields, then there is no header row
        if (_skipLines == -1)
        {
            boolean firstLineCompat = true;
            String[] fields = lineFields[0];
            for (int f = 0; f < nCols; f++)
            {
                if ("".equals(fields[f]))
                    continue;

                try
                {
                    Object o = ConvertUtils.convert(fields[f], colDescs[f].clazz);
                    if (null == o)
                    {
                        firstLineCompat = false;
                        break;
                    }
                }
                catch (Exception x)
                {
                    firstLineCompat = false;
                    break;
                }
            }
            if (firstLineCompat)
                _skipLines = 0;
            else
                _skipLines = 1;
        }

        if (_skipLines > 0)
        {
            String[] headers = lineFields[_skipLines - 1];
            for (int f = 0; f < nCols; f++)
                colDescs[f].name = (f >= headers.length || "".equals(headers[f])) ? getDefaultColumnName(f) : headers[f].trim();
        }
        else
        {
            for (int f = 0; f < colDescs.length; f++)
            {
                ColumnDescriptor colDesc = colDescs[f];
                colDesc.name = getDefaultColumnName(f);
            }
        }

        Set<String> columnNames = new HashSet<String>();
        for (ColumnDescriptor colDesc : colDescs)
        {
            if (!columnNames.add(colDesc.name))
            {
                throw new IOException("All columns must have unique names, but the column name '" + colDesc.name + "' appeared more than once.");
            }
        }

        _columns = colDescs;
    }

    protected String getDefaultColumnName(int col)
    {
        return "column" + col;
    }

    // Given a mv indicator column, find its matching value column
    protected int getMvColumnIndex(ColumnDescriptor mvIndicatorColumn)
    {
        // Sometimes names are URIs, sometimes they're names. If they're URIs, the columns
        // share a name. If not, they have different names
        @Nullable String nonMvIndicatorName = null;
        if (mvIndicatorColumn.name.toLowerCase().endsWith(MvColumn.MV_INDICATOR_SUFFIX.toLowerCase()))
        {
            nonMvIndicatorName = mvIndicatorColumn.name.substring(0, mvIndicatorColumn.name.length() - MvColumn.MV_INDICATOR_SUFFIX.length());
        }
        for(int i = 0; i<_columns.length; i++)
        {
            ColumnDescriptor col = _columns[i];
            if (col.isMvEnabled() && (col.name.equals(mvIndicatorColumn.name) || col.name.equals(nonMvIndicatorName)))
                return i;
        }
        return -1;
    }

    protected int getMvIndicatorColumnIndex(ColumnDescriptor mvColumn)
    {
        // Sometimes names are URIs, sometimes they're names. If they're URIs, the columns
        // share a name. If not, they have different names
        String namePlusIndicator = mvColumn.name + MvColumn.MV_INDICATOR_SUFFIX;

        for(int i = 0; i<_columns.length; i++)
        {
            ColumnDescriptor col = _columns[i];
            if (col.isMvIndicator() && (col.name.equals(mvColumn.name) || col.name.equals(namePlusIndicator)))
                return i;
        }
        return -1;
    }

    /**
     * Set the number of lines to look ahead in the file when infering the data types of the columns.
     */
    public void setScanAheadLineCount(int count)
    {
        _scanAheadLineCount = count;
    }

    /**
     * Returns an iterator over the data
     */
    public abstract CloseableIterator<T> iterator();

    public static class CloseableFilterIterator<T> extends FilterIterator<T> implements CloseableIterator<T>
    {
        protected CloseableIterator<T> _iter;

        public CloseableFilterIterator(CloseableIterator<T> iter, Filter<T> filter)
        {
            super(iter, filter);
            _iter = iter;
        }

        public void close() throws IOException
        {
            _iter.close();
        }
    }


    /**
     * Returns a list of T records, one for each non-header row of the file.
     */
    public List<T> load() throws IOException
    {
        getColumns();

        List<T> rowList = new LinkedList<T>();
        CloseableIterator<T> it = iterator();

        try
        {
            while (it.hasNext())
                rowList.add(it.next());
        }
        finally
        {
            it.close();
        }

        return rowList;
    }

    public abstract void close();


    protected abstract class DataLoaderIterator implements CloseableIterator<Map<String, Object>>
    {
        private final RowMapFactory<Object> factory;

        private Object[] fields = null;
        private Map<String, Object> values = null;
        private int lineNum = 0;
        private boolean skipEmpty = false;

        protected DataLoaderIterator(int lineNum, boolean skipEmpty) throws IOException
        {
            this.lineNum = lineNum;
            this.skipEmpty = skipEmpty;

            Map<String, Integer> colMap = new CaseInsensitiveHashMap<Integer>();
            ColumnDescriptor[] columns = getColumns();

            for (int i = 0; i < columns.length; i++)
            {
                if (columns[i].load && !columns[i].isMvIndicator())
                    colMap.put(columns[i].name, i);
            }

            factory = new RowMapFactory<Object>(colMap);

            // find a converter for each column type
            for (ColumnDescriptor column : _columns)
                if (column.converter == null)
                    column.converter = ConvertUtils.lookup(column.clazz);
        }

        public int lineNum()
        {
            return lineNum;
        }

        protected abstract Object[] readFields();

        public Map<String, Object> next()
        {
            if (values == null)
                throw new IllegalStateException("Attempt to call next() on a finished iterator");
            Map<String, Object> next = values;
            values = null;
            return next;
        }

        public boolean hasNext()
        {
            if (fields != null)
                return true;    // throw illegalstate?

            try
            {
                while (true)
                {
                    fields = readFields();
                    if (fields == null)
                    {
                        close();
                        return false;
                    }
                    lineNum++;

                    values = convertValues();
                    if (values == Collections.EMPTY_MAP && skipEmpty)
                        continue;

                    return values != null;
                }
            }
            catch (Exception e)
            {
                _log.error("unexpected io error", e);
                throw new RuntimeException(e);
            }
        }

        protected Map<String, Object> convertValues()
        {
            if (fields == null)
                return null;    // consider: throw IllegalState

            try
            {
                Object[] fields = this.fields;
                this.fields = null;
                Object[] values = new Object[_columns.length];

                boolean foundData = false;
                for (int i = 0; i < _columns.length; i++)
                {
                    ColumnDescriptor column = _columns[i];
                    if (!column.load)
                        continue;
                    Object fld;
                    if (i >= fields.length)
                    {
                        fld = "";
                    }
                    else
                    {
                        fld = fields[i];
                    }
                    try
                    {
                        if (column.isMvEnabled())
                        {
                            if (values[i] != null)
                            {
                                // An MV indicator column must have generated this. Set the value
                                MvFieldWrapper mvWrapper = (MvFieldWrapper)values[i];
                                mvWrapper.setValue(("".equals(fld)) ?
                                    column.missingValues :
                                    column.converter.convert(column.clazz, fld));
                            }
                            else
                            {
                                // Do we have an MV indicator column?
                                int mvIndicatorIndex = getMvIndicatorColumnIndex(column);
                                if (mvIndicatorIndex != -1)
                                {
                                    // There is such a column, so this value had better be good.
                                    MvFieldWrapper mvWrapper = new MvFieldWrapper();
                                    mvWrapper.setValue( ("".equals(fld)) ?
                                        column.missingValues :
                                        column.converter.convert(column.clazz, fld));
                                    values[i] = mvWrapper;
                                    values[mvIndicatorIndex] = mvWrapper;
                                }
                                else
                                {
                                    // No such column. Is this a valid MV indicator or a valid value?
                                    if (MvUtil.isValidMvIndicator(fld.toString(), column.getMvContainer()))
                                    {
                                        MvFieldWrapper mvWrapper = new MvFieldWrapper();
                                        mvWrapper.setMvIndicator("".equals(fld) ? null : fld.toString());
                                        values[i] = mvWrapper;
                                    }
                                    else
                                    {
                                        MvFieldWrapper mvWrapper = new MvFieldWrapper();
                                        mvWrapper.setValue( ("".equals(fld)) ?
                                            column.missingValues :
                                            column.converter.convert(column.clazz, fld));
                                        values[i] = mvWrapper;
                                    }
                                }
                            }
                        }
                        else if (column.isMvIndicator())
                        {
                            int qcColumnIndex = getMvColumnIndex(column);
                            if (qcColumnIndex != -1)
                            {
                                // There's an mv column that matches
                                if (values[qcColumnIndex] == null)
                                {
                                    MvFieldWrapper mvWrapper = new MvFieldWrapper();
                                    mvWrapper.setMvIndicator("".equals(fld) ? null : fld.toString());
                                    values[qcColumnIndex] = mvWrapper;
                                    values[i] = mvWrapper;
                                }
                                else
                                {
                                    MvFieldWrapper mvWrapper = (MvFieldWrapper)values[qcColumnIndex];
                                    mvWrapper.setMvIndicator("".equals(fld) ? null : fld.toString());
                                }
                                if (_throwOnErrors && !MvUtil.isValidMvIndicator(fld.toString(), column.getMvContainer()))
                                    throw new ConversionException(fld + " is not a valid MV indicator");
                            }
                            else
                            {
                                // No matching mv column, just put in a wrapper
                                if (!MvUtil.isValidMvIndicator(fld.toString(), column.getMvContainer()))
                                {
                                    throw new ConversionException(fld + " is not a valid MV indicator");
                                }
                                MvFieldWrapper mvWrapper = new MvFieldWrapper();
                                mvWrapper.setMvIndicator("".equals(fld) ? null : fld.toString());
                                values[i] = mvWrapper;
                            }
                        }
                        else
                        {
                            values[i] = ("".equals(fld)) ?
                                    column.missingValues :
                                    column.converter.convert(column.clazz, fld);
                        }
                    }
                    catch (Exception x)
                    {
                        if (_throwOnErrors)
                        {
                            StringBuilder sb = new StringBuilder("Could not convert the ");
                            if (fields[i] == null)
                            {
                                sb.append("empty value");
                            }
                            else
                            {
                                sb.append("value '");
                                sb.append(fields[i]);
                                sb.append("'");
                            }
                            sb.append(" from line #");
                            sb.append(lineNum);
                            sb.append(" in column #");
                            sb.append(i + 1);
                            sb.append(" (");
                            if (column.name.indexOf("#") != -1)
                            {
                                sb.append(column.name.substring(column.name.indexOf("#") + 1));
                            }
                            else
                            {
                                sb.append(column.name);
                            }
                            sb.append(") to ");
                            sb.append(column.clazz.getSimpleName());

                            throw new ConversionException(sb.toString(), x);
                        }

                        values[i] = column.errorValues;
                    }

                    if (values[i] != null)
                        foundData = true;
                }

                if (foundData)
                {
                    ArrayList<Object> list = new ArrayList<Object>((int)(_columns.length * 1.2));
                    list.addAll(Arrays.asList(values));
                    return factory.getRowMap(list);
                }
                else if (skipEmpty)
                {
                    return Collections.EMPTY_MAP;
                }
            }
            catch (Exception e)
            {
                if (_throwOnErrors)
                {
                    if (e instanceof ConversionException)
                        throw ((ConversionException) e);
                    else
                        throw new RuntimeException(e);
                }

                if (null != _file)
                    _log.error("failed loading file " + _file.getName() + " at line: " + lineNum + " " + e, e);
            }
            
            return null;
        }


        public void remove()
        {
            throw new UnsupportedOperationException("'remove()' is not defined for TabLoaderIterator");
        }
    }

}
