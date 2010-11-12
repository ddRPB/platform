/*
 * Copyright (c) 2005-2010 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.CompareType.CompareClause;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.collections.CaseInsensitiveHashSet;

import java.util.*;

/**
 * User: arauch
 * Date: Jan 11, 2005
 * Time: 8:04:37 AM
 */
public class SimpleFilter implements Filter
{
    public static abstract class FilterClause
    {
        private boolean _urlClause = false;
        private Object[] _paramVals = new Object[0];


        boolean isUrlClause()
        {
            return _urlClause;
        }


        void setUrlClause(boolean urlClause)
        {
            _urlClause = urlClause;
        }


        public Object[] getParamVals()
        {
            return _paramVals;
        }


        public void setParamVals(Object[] paramVals)
        {
            _paramVals = paramVals;
        }


        protected void appendFilterText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            int fromIndex = sb.length();
            appendSqlText(sb, formatter);
            replaceParamValues(sb, fromIndex);
        }


        // FromIndex ensures we're replacing '?'s in this clause only (not earlier clauses with param values containing '?')
        protected void replaceParamValues(StringBuilder sb, int fromIndex)
        {
            Object[] paramVals = getParamVals();

            if (null == paramVals)
                return;

            for (Object o : paramVals)
            {
                String param = o.toString();
                int i = sb.indexOf("?", fromIndex);
                fromIndex += param.length();         // Protects against previous param values in this clause containing '?'
                sb.replace(i, i + 1, param);
            }
        }


        protected void appendSqlText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            SQLFragment sqlf = toSQLFragment(Collections.<String, ColumnInfo>emptyMap(), null);
            if (sqlf.isEmpty())
                sb.append("1=1");
            else
                sb.append(sqlf);
        }

        abstract public List<String> getColumnNames();

        /** @return whether the value meets the criteria of this filter */
        public boolean meetsCriteria(Object value)
        {
            return false;
        }

        public abstract SQLFragment toSQLFragment(Map<String, ? extends ColumnInfo> columnMap, SqlDialect dialect);
    }

    public static class SQLClause extends FilterClause
    {
        String _fragment;
        private List<String> _colNames = new ArrayList<String>();


        public SQLClause(String fragment, Object[] paramVals, String... colNames)
        {
            setUrlClause(false);
            _fragment = fragment;
            if (paramVals == null)
            {
                paramVals = new Object[0];
            }
            setParamVals(paramVals);
            _colNames = Arrays.asList(colNames);
        }

        public SQLFragment toSQLFragment(Map<String, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            return new SQLFragment(_fragment, getParamVals());
        }

        public List<String> getColumnNames()
        {
            return _colNames;
        }
    }

    public abstract static class OperationClause extends FilterClause
    {
        private List<FilterClause> _clauses;
        private String _operation;

        protected OperationClause(String operation, FilterClause... clauses)
        {
            _operation = operation;
            _clauses = new ArrayList<FilterClause>(Arrays.asList(clauses));
        }

        public List<String> getColumnNames()
        {
            List<String> result = new ArrayList<String>();
            for (FilterClause clause : _clauses)
            {
                result.addAll(clause.getColumnNames());
            }
            return result;
        }

        public void addClause(FilterClause clause)
        {
            _clauses.add(clause);
        }

        public Object[] getParamVals()
        {
            List<Object> result = new ArrayList<Object>();
            for (FilterClause clause : _clauses)
            {
                result.addAll(Arrays.asList(clause.getParamVals()));
            }
            return result.toArray(new Object[result.size()]);
        }

        public SQLFragment toSQLFragment(Map<String, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            SQLFragment sqlFragment = new SQLFragment();
            String separator = "";
            for (FilterClause clause : _clauses)
            {
                sqlFragment.append(separator);
                separator = _operation;
                sqlFragment.append("(");
                sqlFragment.append(clause.toSQLFragment(columnMap, dialect));
                sqlFragment.append(")");
            }
            
            return sqlFragment;
        }

        protected List<FilterClause> getClauses()
        {
            return _clauses;
        }
    }

    public static class OrClause extends OperationClause
    {
        public OrClause(FilterClause... clauses)
        {
            super(" OR ", clauses);
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            for (FilterClause clause : getClauses())
            {
                if (clause.meetsCriteria(value))
                {
                    return true;
                }
            }
            return false;
        }
    }

    public static class AndClause extends OperationClause
    {
        public AndClause(FilterClause... clauses)
        {
            super(" AND ", clauses);
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            for (FilterClause clause : getClauses())
            {
                if (!clause.meetsCriteria(value))
                {
                    return false;
                }
            }
            return !getClauses().isEmpty();
        }
    }

    public static class NotClause extends FilterClause
    {
        private FilterClause _clause;

        public NotClause(FilterClause clause)
        {
            _clause = clause;
        }

        public List<String> getColumnNames()
        {
            return _clause.getColumnNames();
        }

        public Object[] getParamVals()
        {
            return _clause.getParamVals();
        }

        public SQLFragment toSQLFragment(Map<String, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            SQLFragment sqlFragment = new SQLFragment();
            sqlFragment.append(" NOT (");
            sqlFragment.append(_clause.toSQLFragment(columnMap, dialect));
            sqlFragment.append(")");
            return sqlFragment;
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            return !_clause.meetsCriteria(value);
        }
    }

    public static class InClause extends FilterClause
    {
        private String _colName;

        public InClause(String colName, Collection params)
        {
            this(colName, params, false);
        }

        public InClause(String colName, Collection params, boolean urlClause)
        {
            setUrlClause(urlClause);
            setParamVals(params.toArray());
            _colName = colName;
        }

        public List<String> getColumnNames()
        {
            return Arrays.asList(_colName);
        }

        @Override
        protected void appendFilterText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            sb.append(formatter.format(_colName));
            sb.append(" IS ONE OF (");
            String sep = "";
            for (Object val : getParamVals())
            {
                if (val != null)
                {
                    sb.append(sep).append(val.toString());
                    sep = ", ";
                }
            }
            sb.append(")");
        }

        public SQLFragment toSQLFragment(Map<String, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            Object[] params = getParamVals();

            if (0 == params.length)
                return new SQLFragment("1=0");

            ColumnInfo colInfo = columnMap.get(_colName);
            String alias = _colName;
            if (colInfo != null)
            {
                alias = dialect.getColumnSelectName(colInfo.getAlias());
            }
            SQLFragment in = new SQLFragment(alias);

            in.append(" IN (");

            if (params.length > 0)
            {
                String questionMarks = StringUtils.repeat("?, ", params.length);
                in.append(questionMarks.substring(0, questionMarks.length() - 2));
            }
            else
            {
                in.append("NULL");  // Empty list case; "WHERE column IN (NULL)" should always be false
            }

            in.append(")");

            if (colInfo == null || !isUrlClause() || getParamVals() == null)
            {
                in.addAll(getParamVals());
            }
            else
            {
                for (Object paramVal : getParamVals())
                {
                    in.add(CompareType.convertParamValue(colInfo, paramVal));
                }
            }
            return in;
        }

        public void addInValue(Object... values)
        {
            addInValues(Arrays.asList(values));
        }

        public void addInValues(Collection<?> newValues)
        {
            Set<Object> values = new HashSet<Object>(Arrays.asList(getParamVals()));
            values.addAll(newValues);
            setParamVals(values.toArray());
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            for (Object params : getParamVals())
            {
                // Loop through all the values and check if any of them are equals
                FilterClause compareClause = CompareType.EQUAL.createFilterClause(getColumnNames().get(0), params);
                if (compareClause.meetsCriteria(value))
                {
                    return true;
                }
            }
            return false;
        }
    }

    private ArrayList<FilterClause> _clauses = new ArrayList<FilterClause>();


    public SimpleFilter()
    {
    }

    public SimpleFilter(FilterClause... clauses)
    {
        _clauses = new ArrayList<FilterClause>(Arrays.asList(clauses));
    }

    public SimpleFilter(Filter filter)
    {
        if (null == filter)
            return;
        SimpleFilter src = (SimpleFilter) filter;
        this._clauses = (ArrayList<FilterClause>) src._clauses.clone();
    }

    public SimpleFilter(String colName, Object value)
    {
        addCondition(colName, value);
    }

    public SimpleFilter(String colName, Object value, CompareType compare)
    {
        addCondition(colName, value, compare);
    }

    public SimpleFilter(URLHelper urlHelp, String regionName)
    {
        addUrlFilters(urlHelp, regionName);
    }

    public void addUrlFilters(URLHelper urlHelp, String regionName)
    {
        String prefix = regionName == null ? "" : regionName + ".";
        String[] filterKeys = urlHelp.getKeysByPrefix(prefix);
        for (String filterKey : filterKeys)
        {
            String colTildeCompare = filterKey.substring(prefix.length());

            String[] params = urlHelp.getParameters(filterKey);
            for (String param : params)
            {
                String[] compareInfo = colTildeCompare.split("~");
                CompareType type = null;
                String columnName = compareInfo[0];
                if (compareInfo.length == 2)
                    type = CompareType.getByURLKey(compareInfo[1]);

                if (null == type)
                    continue;

                FilterClause fc = type.createFilterClause(columnName, param);
                fc.setUrlClause(true);
                _clauses.add(fc);
            }
        }
    }

    public SimpleFilter addAllClauses(SimpleFilter filter)
    {
        _clauses.addAll((ArrayList<FilterClause>) filter._clauses.clone());
        return this;
    }

    public SimpleFilter addClause(FilterClause clause)
    {
        _clauses.add(clause);
        return this;
    }

    public List<String> getAllColumnNames()
    {
        List<String> result = new ArrayList<String>();
        for (FilterClause clause : _clauses)
        {
            result.addAll(clause.getColumnNames());
        }
        return result;
    }

    public SimpleFilter deleteConditions(String colName)
    {
        for (Iterator<SimpleFilter.FilterClause> it = _clauses.iterator() ; it.hasNext(); )
        {
            SimpleFilter.FilterClause clause = it.next();
            CaseInsensitiveHashSet names = new CaseInsensitiveHashSet(clause.getColumnNames());
            if (names.contains(colName))
                it.remove();
        }
        return this;
    }

    public SimpleFilter addCondition(String colName, Object value)
    {
        return addCondition(colName, value == null ? Parameter.NULL_MARKER : value, CompareType.EQUAL);
    }

    public SimpleFilter addCondition(ColumnInfo column, Object value)
    {
        return addCondition(column.getAlias(), value == null ? Parameter.NULL_MARKER : value, CompareType.EQUAL);
    }

    public SimpleFilter addCondition(ColumnInfo column, Object value, CompareType compare)
    {
        return addCondition(column.getAlias(), value, compare);
    }

    public SimpleFilter addCondition(String colName, Object value, CompareType compare)
    {
        _clauses.add(compare.createFilterClause(colName, value));
        return this;
    }

    public SimpleFilter addCondition(FilterClause clause)
    {
        _clauses.add(clause);
        return this;
    }

    public SimpleFilter addBetween(String colName, Comparable value1, Comparable value2)
    {
        if (value1 != null && value2 != null && value1.equals(value2))
            addCondition(colName, value1);  // Equal
        else if (value1 != null && value2 != null && value1.compareTo(value2) > 0)
        {
            addCondition(colName, value2, CompareType.GTE);
            addCondition(colName, value1, CompareType.LTE);
        }
        else
        {
            addCondition(colName, value1, CompareType.GTE);
            addCondition(colName, value2, CompareType.LTE);
        }
        return this;
    }

    public SimpleFilter addWhereClause(String fragment, Object[] paramVals, String... colNames)
    {
        _clauses.add(new SQLClause(fragment, paramVals, colNames));
        return this;
    }

    public SimpleFilter addInClause(String colName, Collection paramVals)
    {
        _clauses.add(new InClause(colName, paramVals));
        return this;
    }

    public String toQueryString(String regionName)
    {
        StringBuilder ret = new StringBuilder();
        String prefix = regionName == null ? "" : regionName + ".";
        String and = "";
        for (FilterClause fc : _clauses)
        {
            if (fc instanceof CompareClause)
            {
                CompareClause cc = (CompareClause) fc;
                ret.append(and);
                and = "&";
                ret.append(PageFlowUtil.encode(prefix + cc._colName + "~" + cc._comparison.getPreferredUrlKey()));
                if (cc.getParamVals() != null && cc.getParamVals()[0] != null)
                {
                    ret.append("=");
                    ret.append(PageFlowUtil.encode(cc.getParamVals()[0].toString()));
                }
            }
        }
        return ret.toString();
    }

    /**
     * Apply the filters in this SimpleFilter to the url scoped by the regionName prefix.
     * @param url The url to be modified.
     * @param regionName The prefix used to scope the filters.
     */
    public void applyToURL(URLHelper url, String regionName)
    {
        String prefix = regionName == null ? "" : regionName + ".";
        for (FilterClause fc : _clauses)
        {
            String urlType = null;
            String value = null;
            if (fc instanceof CompareClause)
            {
                CompareClause cc = (CompareClause) fc;
                urlType = cc._comparison.getPreferredUrlKey();
                value = cc.getParamVals() != null && cc.getParamVals()[0] != null ?
                        cc.getParamVals()[0].toString() : null;
            }
            else if (fc instanceof SimpleFilter.InClause)
            {
                SimpleFilter.InClause inClause = (SimpleFilter.InClause)fc;
                urlType = CompareType.IN.getPreferredUrlKey();
                StringBuilder values = new StringBuilder();
                String separator = "";
                for (Object inValue : inClause.getParamVals())
                {
                    values.append(separator);
                    separator = ";";
                    values.append(inValue == null ? "" : inValue.toString());
                }
                value = values.toString();
            }

            if (urlType != null)
            {
                String key = prefix + fc.getColumnNames().get(0) + "~" + urlType;
                url.addParameter(key, value);
            }
        }
    }

    /*
    // UNDONE encode()
    public String getParamString()
    {
        StringBuffer sb = new StringBuffer();
        String and = "";
        for (FilterClause fc : _clauses)
        {
            if (fc.isUrlClause() && fc instanceof CompareClause)
            {
                CompareClause cc = (CompareClause) fc;
                sb.append(and);
                sb.append(_regionName);
                sb.append('.');
                sb.append(cc._colName);
                sb.append(cc._comparison);
                sb.append('=');
                sb.append(cc.getParamVals()[0].toString());
                and = "&";
            }
        }
        return sb.toString();
    }
    */


    public SQLFragment getSQLFragment(TableInfo tableInfo, List<ColumnInfo> colInfos)
    {
        if (null == _clauses || 0 == _clauses.size())
        {
            return new SQLFragment();
        }
        return getSQLFragment(tableInfo.getSqlDialect(), Table.createColumnMap(tableInfo, colInfos));
    }

    //
    // Filter
    //


    public SQLFragment getSQLFragment(SqlDialect dialect)
    {
        return getSQLFragment(dialect, new HashMap<String, ColumnInfo>());
    }

    public SQLFragment getSQLFragment(SqlDialect dialect, Map<String, ? extends ColumnInfo> columnMap)
    {
        SQLFragment ret = new SQLFragment();

        if (null == _clauses || 0 == _clauses.size())
            return ret;

        String sAND = "WHERE ";

        for (FilterClause fc : _clauses)
        {
            ret.append(sAND);
            ret.append("(");
            ret.append(fc.toSQLFragment(columnMap, dialect));
            ret.append(")");
            sAND = " AND ";
        }

        return ret;
    }


    public List<Object> getWhereParams(TableInfo tableInfo)
    {
        return getWhereParams(tableInfo, tableInfo.getColumns());
    }


    public List<Object> getWhereParams(TableInfo tableInfo, List<ColumnInfo> colInfos)
    {
        SQLFragment frag = getSQLFragment(tableInfo, colInfos);
        return frag.getParams();
    }


    public Set<String> getWhereParamNames()
    {
        Set<String> paramNames = new HashSet<String>(_clauses.size());

        for (FilterClause fc : _clauses)
            paramNames.addAll(fc.getColumnNames());

        return paramNames;
    }

    public List<FilterClause> getClauses()
    {
        return Collections.unmodifiableList(_clauses);
    }

    public String getWhereSQL(SqlDialect dialect)
    {
        SQLFragment frag = getSQLFragment(dialect);
        return frag.getSQL();
    }

    public String getFilterText()
    {
        return getFilterText(new ColumnNameFormatter());
    }

    // Display pseudo English version of filter
    public String getFilterText(ColumnNameFormatter formatter)
    {
        StringBuilder sb = new StringBuilder();
        String sAND = "";

        for (FilterClause clause : _clauses)
        {
            sb.append(sAND);
            sb.append('(');
            clause.appendFilterText(sb, formatter);
            sb.append(')');
            sAND = " AND ";
        }

        return sb.toString();
    }

    public String toSQLString(SqlDialect dialect)
    {
        SQLFragment fragment = getSQLFragment(dialect);
        return dialect.substituteParameters(fragment);
    }

    public boolean hasContainerEqualClause()
    {
        for (FilterClause clause : _clauses)
        {
            if (clause instanceof CompareClause)
            {
                CompareClause compClause = (CompareClause) clause;
                if (compClause.getComparison() == CompareType.EQUAL &&
                        compClause.getColumnNames().size() == 1 &&
                        "container".equalsIgnoreCase(compClause.getColumnNames().get(0)))
                    return true;
            }
            if (clause instanceof InClause)
            {
                InClause inClause = (InClause)clause;
                if (inClause.getColumnNames().size() == 1 &&
                        "container".equalsIgnoreCase(inClause.getColumnNames().get(0)))
                {
                    return true;
                }
            }
        }
        return false;
    }

    // Take care of column name artifacts
    public static class ColumnNameFormatter
    {
        public String format(String columnName)
        {
            return FieldKey.fromString(columnName).getDisplayString();
        }
    }
}
