package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.DataClassUserSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.StringExpression;

/**
 * User: kevink
 * Date: 2/23/16
 */
public class LineageTableInfo extends VirtualTable
{
    private @NotNull SQLFragment _lsids;
    private boolean _parents;
    private @Nullable Integer _depth;
    private @Nullable String _expType;
    private @Nullable String _cpasType;

    public LineageTableInfo(String name, @NotNull UserSchema schema, @NotNull SQLFragment lsids, boolean parents, @Nullable Integer depth, @Nullable String expType, @Nullable String cpasType)
    {
        super(schema.getDbSchema(), name, schema);
        _lsids = lsids;
        _parents = parents;

        // depth is negative for parent values
        if (depth != null && depth > 0 && _parents)
            depth = -1 * depth;
        _depth = depth;
        _expType = expType;
        _cpasType = cpasType;

        ColumnInfo selfLsid = new ColumnInfo(FieldKey.fromParts("self_lsid"), this, JdbcType.VARCHAR);
        selfLsid.setSqlTypeName("lsidtype");
        addColumn(selfLsid);

        ColumnInfo selfRowId = new ColumnInfo(FieldKey.fromParts("self_rowid"), this, JdbcType.INTEGER);
        addColumn(selfRowId);

        ColumnInfo depthCol = new ColumnInfo(FieldKey.fromParts("depth"), this, JdbcType.INTEGER);
        addColumn(depthCol);

        ColumnInfo parentContainer = new ColumnInfo(FieldKey.fromParts("parent_container"), this, JdbcType.VARCHAR);
        parentContainer.setSqlTypeName("entityid");
        ContainerForeignKey.initColumn(parentContainer, schema);
        addColumn(parentContainer);

        ColumnInfo parentExpType = new ColumnInfo(FieldKey.fromParts("parent_exptype"), this, JdbcType.VARCHAR);
        addColumn(parentExpType);

        ColumnInfo parentCpasType = new ColumnInfo(FieldKey.fromParts("parent_cpastype"), this, JdbcType.VARCHAR);
        addColumn(parentCpasType);

        ColumnInfo parentName = new ColumnInfo(FieldKey.fromParts("parent_name"), this, JdbcType.VARCHAR);
        addColumn(parentName);

        ColumnInfo parentLsid = new ColumnInfo(FieldKey.fromParts("parent_lsid"), this, JdbcType.VARCHAR);
        parentLsid.setSqlTypeName("lsidtype");
        parentLsid.setFk(createLsidLookup(_expType, _cpasType));
        addColumn(parentLsid);

        ColumnInfo parentRowId = new ColumnInfo(FieldKey.fromParts("parent_rowId"), this, JdbcType.INTEGER);
        //parentRowId.setFk(new QueryForeignKey("exp", schema.getContainer(), schema.getContainer(), schema.getUser(), "Materials", "rowId", "Name"));
        addColumn(parentRowId);

        ColumnInfo role = new ColumnInfo(FieldKey.fromParts("role"), this, JdbcType.VARCHAR);
        addColumn(role);

        ColumnInfo childContainer = new ColumnInfo(FieldKey.fromParts("child_container"), this, JdbcType.VARCHAR);
        childContainer.setSqlTypeName("entityid");
        ContainerForeignKey.initColumn(childContainer, schema);
        addColumn(childContainer);

        ColumnInfo childExpType = new ColumnInfo(FieldKey.fromParts("child_exptype"), this, JdbcType.VARCHAR);
        addColumn(childExpType);

        ColumnInfo childCpasType = new ColumnInfo(FieldKey.fromParts("child_cpastype"), this, JdbcType.VARCHAR);
        addColumn(childCpasType);

        ColumnInfo childName = new ColumnInfo(FieldKey.fromParts("child_name"), this, JdbcType.VARCHAR);
        addColumn(childName);

        ColumnInfo childLsid = new ColumnInfo(FieldKey.fromParts("child_lsid"), this, JdbcType.VARCHAR);
        childLsid.setSqlTypeName("lsidtype");
        childLsid.setFk(createLsidLookup(_expType, _cpasType));
        addColumn(childLsid);

        ColumnInfo childRowId = new ColumnInfo(FieldKey.fromParts("child_rowId"), this, JdbcType.INTEGER);
        addColumn(childRowId);

    }

    private ForeignKey createLsidLookup(String expType, String cpasType)
    {
        ForeignKey fk = null;
        if (cpasType != null)
            fk = createCpasTypeFK(cpasType);
        else if (expType != null)
            fk = createExpTypeFK(expType);

        if (fk != null)
            return fk;

        return new LookupForeignKey("lsid") {
            @Override
            public TableInfo getLookupTableInfo() { return new NodesTableInfo(_userSchema); }
        };
    }

    private ForeignKey createExpTypeFK(String expType)
    {
        switch (expType) {
            case "Data":
                return new QueryForeignKey("exp", _userSchema.getContainer(), null, _userSchema.getUser(), "Data", "LSID", "Name");
            case "Material":
                return new QueryForeignKey("exp", _userSchema.getContainer(), null, _userSchema.getUser(), "Materials", "LSID", "Name");
            case "ExperimentRun":
                return new QueryForeignKey("exp", _userSchema.getContainer(), null, _userSchema.getUser(), "Runs", "LSID", "Name");
            default:
                return null;
        }
    }

    private ForeignKey createCpasTypeFK(String cpasType)
    {
        ExpSampleSet ss = ExperimentService.get().getSampleSet(cpasType);
        if (ss != null)
        {
            return new LookupForeignKey("lsid", "Name")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    SamplesSchema samplesSchema = new SamplesSchema(_userSchema.getUser(), _userSchema.getContainer());
                    return samplesSchema.getSampleTable(ss);
                }

                @Override
                public StringExpression getURL(ColumnInfo parent)
                {
                    return super.getURL(parent, true);
                }
            };
        }

        ExpDataClass dc = ExperimentService.get().getDataClass(cpasType);
        if (dc != null)
        {
            return new LookupForeignKey("lsid", "Name")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    DataClassUserSchema dcus = new DataClassUserSchema(_userSchema.getContainer(), _userSchema.getUser());
                    return dcus.createTable(dc);
                }

                @Override
                public StringExpression getURL(ColumnInfo parent)
                {
                    return super.getURL(parent, true);
                }
            };
        }

        return null;
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL()
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setParents(_parents);
        options.setChildren(!_parents);
        options.setCpasType(_cpasType);
        options.setExpType(_expType);
        if (_depth != null)
            options.setDepth(_depth);

        SQLFragment tree = ExperimentServiceImpl.get().generateExperimentTreeSQL(_lsids, options);

        String comment = String.format("<LineageTableInfo parents=%b, depth=%d, expType=%s, cpasType=%s>\n", _parents, _depth, _expType, _cpasType);

        SQLFragment sql = new SQLFragment();
        sql.appendComment(comment, getSqlDialect());
        sql.append(tree);
        sql.appendComment("</LineageTableInfo>\n", getSqlDialect());

        return sql;
    }

    /**
     * Union of all Data, Material, and ExperimentRun rows for use as a generic lookup target.
     */
    private static class NodesTableInfo extends VirtualTable
    {
        public NodesTableInfo(@Nullable UserSchema schema)
        {
            super(schema.getDbSchema(), "Nodes", schema);

            ColumnInfo containerCol = new ColumnInfo(FieldKey.fromParts("Container"), this, JdbcType.VARCHAR);
            containerCol.setSqlTypeName("entityid");
            ContainerForeignKey.initColumn(containerCol, schema);
            addColumn(containerCol);

            ColumnInfo name = new ColumnInfo(FieldKey.fromParts("name"), this, JdbcType.VARCHAR);
            name.setURL(DetailsURL.fromString("experiment/resolveLsid.view?lsid=${LSID}&type=${exptype}"));
            addColumn(name);

            ColumnInfo expType = new ColumnInfo(FieldKey.fromParts("exptype"), this, JdbcType.VARCHAR);
            addColumn(expType);

            ColumnInfo cpasType = new ColumnInfo(FieldKey.fromParts("cpastype"), this, JdbcType.VARCHAR);
            addColumn(cpasType);

            ColumnInfo lsid = new ColumnInfo(FieldKey.fromParts("lsid"), this, JdbcType.VARCHAR);
            lsid.setSqlTypeName("lsidtype");
            addColumn(lsid);

            ColumnInfo rowId = new ColumnInfo(FieldKey.fromParts("rowId"), this, JdbcType.INTEGER);
            addColumn(rowId);

        }

        @NotNull
        @Override
        public SQLFragment getFromSQL()
        {
            SQLFragment sql = new SQLFragment();
            sql.append(
                    "SELECT container, CAST('Data' AS VARCHAR(50)) AS exptype, CAST(cpastype AS VARCHAR(200)) AS cpastype, name, lsid, rowid\n" +
                    "FROM exp.Data\n" +
                    "\n" +
                    "UNION ALL\n" +
                    "\n" +
                    "SELECT container, CAST('Material' AS VARCHAR(50)) AS exptype, CAST(cpastype AS VARCHAR(200)) AS cpastype, name, lsid, rowid\n" +
                    "FROM exp.Material\n" +
                    "\n" +
                    "UNION ALL\n" +
                    "\n" +
                    "SELECT container, CAST('ExperimentRun' AS VARCHAR(50)) AS exptype, CAST(NULL AS VARCHAR(200)) AS cpastype, name, lsid, rowid\n" +
                    "FROM exp.ExperimentRun\n");
            return sql;
        }
    }

}
