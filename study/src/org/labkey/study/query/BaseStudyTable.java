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

package org.labkey.study.query;

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.io.IOException;
import java.io.Writer;
import java.sql.Types;
import java.util.Map;

public abstract class BaseStudyTable extends FilteredTable
{
    protected StudyQuerySchema _schema;
    public BaseStudyTable(StudyQuerySchema schema, TableInfo realTable)
    {
        super(realTable, schema.getContainer());
        _schema = schema;
    }

    protected ColumnInfo addWrapParticipantColumn(String rootTableColumnName)
    {
        ColumnInfo participantColumn = new AliasedColumn(this, "ParticipantId", _rootTable.getColumn(rootTableColumnName));
        participantColumn.setFk(new QueryForeignKey(_schema, "Participant", "ParticipantId", null));
        participantColumn.setKeyField(true);
        return addColumn(participantColumn);
    }

    protected ColumnInfo addWrapLocationColumn(String wrappedName, String rootTableColumnName)
    {
        ColumnInfo originatingSiteCol = new AliasedColumn(this, wrappedName, _rootTable.getColumn(rootTableColumnName));
        originatingSiteCol.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                SiteTable result = new SiteTable(_schema);
                result.setContainerFilter(ContainerFilter.EVERYTHING);
                return result;
            }
        });
        return addColumn(originatingSiteCol);
    }

    protected ColumnInfo addWrapTypeColumn(String wrappedName, final String rootTableColumnName)
    {
        ColumnInfo typeColumn = new AliasedColumn(this, wrappedName, _rootTable.getColumn(rootTableColumnName));
        LookupForeignKey fk = new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                BaseStudyTable result;
                if (rootTableColumnName.equals("PrimaryTypeId"))
                    result = new PrimaryTypeTable(_schema);
                else if (rootTableColumnName.equals("DerivativeTypeId") || rootTableColumnName.equals("DerivativeTypeId2"))
                    result = new DerivativeTypeTable(_schema);
                else if (rootTableColumnName.equals("AdditiveTypeId"))
                    result = new AdditiveTypeTable(_schema);
                else
                    throw new IllegalStateException(rootTableColumnName + " is not recognized as a valid specimen type column.");
                result.setContainerFilter(ContainerFilter.EVERYTHING);
                return result;
            }
        };
        typeColumn.setFk(fk);

        return addColumn(typeColumn);
    }

    protected void addSpecimenVisitColumn(boolean dateBased)
    {
        ColumnInfo visitColumn;
        ColumnInfo visitDescriptionColumn = addWrapColumn(_rootTable.getColumn("VisitDescription"));
        if (dateBased)
        {
            //consider:  use SequenceNumMin for visit-based studies too (in visit-based studies VisitValue == SequenceNumMin)
            // could change to visitrowid but that changes datatype and displays rowid
            // instead of sequencenum when label is null
            visitColumn = addColumn(new DateVisitColumn(this));
            visitColumn.setLabel("Timepoint");
            visitDescriptionColumn.setHidden(true);
        }
        else
        {
            visitColumn = addColumn(new AliasedColumn(this, "Visit", _rootTable.getColumn("VisitValue")));
        }

        LookupForeignKey visitFK = new LookupForeignKey(null, (String) null, "SequenceNumMin", null)
        {
            public TableInfo getLookupTableInfo()
            {
                VisitTable visitTable = new VisitTable(_schema);
                visitTable.setContainerFilter(ContainerFilter.EVERYTHING);
                return visitTable;
            }
        };
        visitFK.setJoinOnContainer(true);
        visitColumn.setFk(visitFK);
        visitColumn.setKeyField(true);
    }

    private static class DateVisitColumn extends ExprColumn
    {
        private static final String DATE_VISIT_JOIN_ALIAS = "DateVisitJoin";
        public DateVisitColumn(TableInfo parent)
        {
            super(parent, "Visit", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "$" + DATE_VISIT_JOIN_ALIAS + ".SequenceNumMin"), Types.VARCHAR);
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            String pvAlias = parentAlias + "$PV";
            String dateVisitJoinAlias = parentAlias + "$" + DATE_VISIT_JOIN_ALIAS;
            SQLFragment join = new SQLFragment();
            join.append(" LEFT OUTER JOIN " + StudySchema.getInstance().getTableInfoParticipantVisit() + " " + pvAlias + " ON\n" +
                    parentAlias + ".Ptid = " + pvAlias + ".ParticipantId AND " +
                    parentAlias + ".VisitValue = " + pvAlias + ".SequenceNum AND " +
                    parentAlias + ".Container = " + pvAlias + ".Container\n");
            join.append("LEFT OUTER JOIN " + StudySchema.getInstance().getTableInfoVisit() + " " + dateVisitJoinAlias +
                    " ON " + dateVisitJoinAlias + ".RowId = " + pvAlias + ".VisitRowId");
            map.put(DATE_VISIT_JOIN_ALIAS, join);
        }
    }

    protected void addVialCommentsColumn(final boolean joinBackToSpecimens)
    {
        ColumnInfo commentsColumn = new AliasedColumn(this, "Comments", _rootTable.getColumn("GlobalUniqueId"));
        LookupForeignKey commentsFK = new LookupForeignKey("GlobalUniqueId")
        {
            public TableInfo getLookupTableInfo()
            {
                SpecimenCommentTable result = new SpecimenCommentTable(_schema, joinBackToSpecimens);
                result.setContainerFilter(ContainerFilter.EVERYTHING);
                return result;
            }
        };
        commentsFK.setJoinOnContainer(true);
        commentsColumn.setFk(commentsFK);
        commentsColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new CommentDisplayColumn(colInfo);
            }
        });
        addColumn(commentsColumn);
    }

    protected ColumnInfo createSpecimenCommentColumn(StudyQuerySchema schema, boolean includeVialComments)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(_schema.getContainer());

        DataSetDefinition defPtid = null;
        DataSetDefinition defPtidVisit = null;

        if (study.getParticipantCommentDataSetId() != null)
            defPtid = StudyManager.getInstance().getDataSetDefinition(study, study.getParticipantCommentDataSetId());
        if (study.getParticipantVisitCommentDataSetId() != null)
            defPtidVisit = StudyManager.getInstance().getDataSetDefinition(study, study.getParticipantVisitCommentDataSetId());
        
        TableInfo participantCommentTable = defPtid != null ? defPtid.getTableInfo(schema.getUser()) : null;
        TableInfo participantVisitCommentTable = defPtidVisit != null ? defPtidVisit.getTableInfo(schema.getUser()) : null;

        return new SpecimenCommentColumn(this, participantCommentTable, study.getParticipantCommentProperty(),
                participantVisitCommentTable, study.getParticipantVisitCommentProperty(), includeVialComments);
    }

    public static class SpecimenCommentColumn extends ExprColumn
    {
        public static final String COLUMN_NAME = "SpecimenComment";
        protected static final String VIAL_COMMENT_ALIAS = "VialComment";
        protected static final String PARTICIPANT_COMMENT_JOIN = "ParticipantCommentJoin$";
        protected static final String PARTICIPANT_COMMENT_ALIAS = "ParticipantComment";
        protected static final String PARTICIPANTVISIT_COMMENT_JOIN = "ParticipantVisitCommentJoin$";
        protected static final String PARTICIPANTVISIT_COMMENT_ALIAS = "ParticipantVisitComment";
        protected static final String SPECIMEN_COMMENT_JOIN = "SpecimenCommentJoin$";

        private TableInfo _ptidCommentTable;
        private TableInfo _ptidVisitCommentTable;
        private boolean _includeVialComments;

        public SpecimenCommentColumn(TableInfo parent, TableInfo ptidCommentTable, String ptidCommentProperty,
                                     TableInfo ptidVisitCommentTable, String ptidVisitCommentProperty, boolean includeVialComments)
        {
            super(parent, COLUMN_NAME, new SQLFragment(), Types.VARCHAR);

            _ptidCommentTable = ptidCommentTable;
            _ptidVisitCommentTable = ptidVisitCommentTable;
            _includeVialComments = includeVialComments;

            SQLFragment sql = new SQLFragment();
            String concatOperator = parent.getSchema().getSqlDialect().getConcatenationOperator();

            if (_includeVialComments)
                sql.append(ExprColumn.STR_TABLE_ALIAS + "$" + SPECIMEN_COMMENT_JOIN + ".Comment AS " + VIAL_COMMENT_ALIAS + ",\n");
            if (ptidCommentTable != null && ptidCommentProperty != null)
                sql.append(ExprColumn.STR_TABLE_ALIAS + "$" + PARTICIPANT_COMMENT_JOIN + "." + ptidCommentProperty + " AS " + PARTICIPANT_COMMENT_ALIAS + ",\n");
            if (ptidVisitCommentTable != null && ptidVisitCommentProperty != null)
                sql.append(ExprColumn.STR_TABLE_ALIAS + "$" + PARTICIPANTVISIT_COMMENT_JOIN + "." + ptidVisitCommentProperty + " AS " + PARTICIPANTVISIT_COMMENT_ALIAS + ",\n");

            String concat = "";
            StringBuilder sb = new StringBuilder();
            if (_includeVialComments)
            {
                sb.append(ExprColumn.STR_TABLE_ALIAS + "$" + SPECIMEN_COMMENT_JOIN + ".Comment");
                concat = concatOperator;
            }
            if (ptidCommentTable != null && ptidCommentProperty != null)
            {
                sb.append(concat);
                sb.append(ExprColumn.STR_TABLE_ALIAS + "$" + PARTICIPANT_COMMENT_JOIN + "." + ptidCommentProperty);
                concat = concatOperator;
            }
            if (ptidVisitCommentTable != null && ptidVisitCommentProperty != null)
            {
                sb.append(concat);
                sb.append(ExprColumn.STR_TABLE_ALIAS + "$" + PARTICIPANTVISIT_COMMENT_JOIN + "." + ptidVisitCommentProperty);
            }

            sql.append("(");
            if (sb.length() > 0)
                sql.append(sb.toString());
            else
                sql.append("' '");
            sql.append(")");
            setValueSQL(sql);
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            super.declareJoins(parentAlias, map);

            String tableAlias = parentAlias + "$" + SPECIMEN_COMMENT_JOIN;
            if (map.containsKey(tableAlias))
                return;

            SQLFragment joinSql = new SQLFragment();
            if (_includeVialComments)
            {
                joinSql.append(" LEFT OUTER JOIN ").append(StudySchema.getInstance().getTableInfoSpecimenComment()).append(" AS ");
                joinSql.append(tableAlias).append(" ON ");
                joinSql.append(parentAlias).append(".GlobalUniqueId = ").append(tableAlias).append(".GlobalUniqueId AND ");
                joinSql.append(parentAlias).append(".Container = ").append(tableAlias).append(".Container\n");
            }

            if (_ptidCommentTable != null)
            {
                String ptidTableAlias = parentAlias + "$" + PARTICIPANT_COMMENT_JOIN;

                joinSql.append(" LEFT OUTER JOIN ").append(_ptidCommentTable.getSelectName()).append(" AS ");
                joinSql.append(ptidTableAlias).append(" ON ");
                joinSql.append(parentAlias).append(".Ptid = ").append(ptidTableAlias).append(".ParticipantId\n");
            }

            if (_ptidVisitCommentTable != null)
            {
                String ptidTableAlias = parentAlias + "$" + PARTICIPANTVISIT_COMMENT_JOIN;

                joinSql.append(" LEFT OUTER JOIN ").append(_ptidVisitCommentTable.getSelectName()).append(" AS ");
                joinSql.append(ptidTableAlias).append(" ON ");
                joinSql.append(parentAlias).append(".Ptid = ").append(ptidTableAlias).append(".ParticipantId AND ");
                joinSql.append(parentAlias).append(".VisitValue = ").append(ptidTableAlias).append(".SequenceNum\n");
            }
            map.put(tableAlias, joinSql);
        }
    }

    public static class CommentDisplayColumn extends DataColumn
    {
        public CommentDisplayColumn(ColumnInfo commentColumn)
        {
            super(commentColumn);
        }

        public Object getDisplayValue(RenderContext ctx)
        {
            Object value = getDisplayColumn().getValue(ctx);
            if (value == null)
                return "";
            else
                return value;
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Object value = getDisplayColumn().getValue(ctx);
            if (value != null  && value instanceof String)
                out.write((String) value);
        }
    }

    public static class SpecimenCommentDisplayColumn extends DataColumn
    {
        public SpecimenCommentDisplayColumn(ColumnInfo commentColumn)
        {
            super(commentColumn);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            out.write(formatParticipantComments(ctx, "<br>"));
        }
    }

    protected static String formatParticipantComments(RenderContext ctx, String lineSeparator)
    {
        Map<String, Object> row = ctx.getRow();

        Object vialComment = row.get(SpecimenCommentColumn.VIAL_COMMENT_ALIAS);
        Object participantComment = row.get(SpecimenCommentColumn.PARTICIPANT_COMMENT_ALIAS);
        Object participantVisitComment = row.get(SpecimenCommentColumn.PARTICIPANTVISIT_COMMENT_ALIAS);

        StringBuilder sb = new StringBuilder();

        if (vialComment instanceof String)
        {
            sb.append("<i>vial:&nbsp;</i>");
            sb.append(vialComment);
            sb.append(lineSeparator);
        }
        if (participantComment instanceof String)
        {
            sb.append("<i>participant:&nbsp;</i>");
            sb.append(participantComment);
            sb.append(lineSeparator);
        }
        if (participantVisitComment instanceof String)
        {
            sb.append("<i>participant/visit:&nbsp;</i>");
            sb.append(participantVisitComment);
            sb.append(lineSeparator);
        }
        return sb.toString();
    }
}
