/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.experiment.api;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.cache.DbCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolAction;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.experiment.DotGraph;
import org.labkey.experiment.ExperimentRunGraph;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class ExpRunImpl extends ExpIdentifiableEntityImpl<ExperimentRun> implements ExpRun, AttachmentParent
{
    private boolean _populated;

    private ExpProtocolApplicationImpl[] _protocolSteps;
    private Map<ExpMaterial, String> _materialInputs = new HashMap<>();
    private Map<ExpData, String> _dataInputs = new HashMap<>();
    private List<ExpMaterial> _materialOutputs = new ArrayList<>();
    private List<ExpData> _dataOutputs = new ArrayList<>();
    private ExpRunImpl _replacedByRun;
    private static final Logger LOG = Logger.getLogger(ExpRunImpl.class);

    static public ExpRunImpl[] fromRuns(ExperimentRun[] runs)
    {
        ExpRunImpl[] ret = new ExpRunImpl[runs.length];
        for (int i = 0; i < runs.length; i ++)
        {
            ret[i] = new ExpRunImpl(runs[i]);
        }
        return ret;
    }
    public ExpRunImpl(ExperimentRun run)
    {
        super(run);
    }

    public URLHelper detailsURL()
    {
        return new ActionURL(ExperimentController.ShowRunGraphAction.class, getContainer()).addParameter("rowId", getRowId());
    }

    public Container getContainer()
    {
        return _object.getContainer();
    }

    public int getRowId()
    {
        return _object.getRowId();
    }

    public ExpExperimentImpl[] getExperiments()
    {
        final String sql= " SELECT E.* FROM " + ExperimentServiceImpl.get().getTinfoExperiment() + " E "
                        + " INNER JOIN " + ExperimentServiceImpl.get().getTinfoRunList() + " RL ON (E.RowId = RL.ExperimentId) "
                        + " INNER JOIN " + ExperimentServiceImpl.get().getTinfoExperimentRun() + " ER ON (ER.RowId = RL.ExperimentRunId) "
                        + " WHERE ER.LSID = ? AND E.Hidden = ?;"  ;

        return ExpExperimentImpl.fromExperiments(new SqlSelector(ExperimentServiceImpl.get().getExpSchema(), sql, _object.getLSID(), Boolean.FALSE).getArray(Experiment.class));
    }

    public ExpProtocolImpl getProtocol()
    {
        return ExperimentServiceImpl.get().getExpProtocol(_object.getProtocolLSID());
    }

    public ExpData[] getOutputDatas(@Nullable DataType type)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("RunId"), getRowId());
        if (type != null)
        {
            filter.addWhereClause(Lsid.namespaceFilter("LSID", type.getNamespacePrefix()), null);
        }
        return ExpDataImpl.fromDatas(new TableSelector(ExperimentServiceImpl.get().getTinfoData(), filter, null).getArray(Data.class));
    }

    public ExpData[] getInputDatas(@Nullable String inputRole, @Nullable ExpProtocol.ApplicationType applicationType)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT exp.Data.* FROM exp.Data WHERE exp.Data.RowId IN ");
        sql.append("\n(SELECT exp.DataInput.DataId FROM exp.DataInput ");
        sql.append("\nINNER JOIN exp.ProtocolApplication ON exp.DataInput.TargetApplicationId = exp.ProtocolApplication.RowId");
        sql.append("\nWHERE exp.ProtocolApplication.RunId = ?");
        sql.add(getRowId());
        if (inputRole != null)
        {
            sql.append("\nAND exp.DataInput.Role = ? ");
            sql.add(inputRole);
        }
        if (applicationType != null)
        {
            sql.append("\nAND exp.ProtocolApplication.CpasType = ");
            sql.appendStringLiteral(applicationType.toString());
        }
        sql.append(")");
        return ExpDataImpl.fromDatas(new SqlSelector(ExperimentService.get().getSchema(), sql).getArray(Data.class));
    }

    public File getFilePathRoot()
    {
        if (_object.getFilePathRoot() == null)
        {
            return null;
        }
        return new File(_object.getFilePathRoot());
    }

    public void setFilePathRoot(File file)
    {
        ensureUnlocked();
        _object.setFilePathRoot(file == null ? null : file.getAbsolutePath());
    }

    public String urlFlag(boolean flagged)
    {
        return AppProps.getInstance().getContextPath() + "/Experiment/" + (flagged ? "flagRun.gif" : "unflagRun.gif");
    }

    public void save(User user)
    {
        boolean newRun = getRowId() == 0;
        save(user, ExperimentServiceImpl.get().getTinfoExperimentRun());
        if (newRun)
            ExperimentServiceImpl.get().auditRunEvent(user, this.getProtocol(), this, null, this.getProtocol().getName() + " run loaded");
    }

    public void delete(User user)
    {
        if (!getContainer().hasPermission(user, DeletePermission.class))
        {
            throw new UnauthorizedException();
        }
        ExperimentServiceImpl.get().deleteExperimentRunsByRowIds(getContainer(), user, getRowId());
    }

    public void setProtocol(ExpProtocol protocol)
    {
        _object.setProtocolLSID(protocol.getLSID());
    }

    public ExpProtocolApplicationImpl addProtocolApplication(User user, ExpProtocolAction action, ExpProtocol.ApplicationType appType, String name)
    {
        return addProtocolApplication(user, action, appType, name, null, null, null);
    }

    public ExpProtocolApplicationImpl addProtocolApplication(User user, ExpProtocolAction action, ExpProtocol.ApplicationType appType, String name, java.util.Date startTime, java.util.Date endTime, Integer recordCount)
    {
        ensureUnlocked();
        ProtocolApplication pa = new ProtocolApplication();
        if (action == null)
        {
            if (appType != ExpProtocol.ApplicationType.ExperimentRun)
            {
                throw new IllegalArgumentException("Only the ExperimentRun protocol application type is allowed to have a null protocol action.");
            }
            pa.setProtocolLSID(getProtocol().getLSID());
        }
        else
        {
            pa.setProtocolLSID(action.getChildProtocol().getLSID());
            pa.setActionSequence(action.getActionSequence());
        }
        pa.setName(name);
        pa.setLSID(ExperimentService.get().generateGuidLSID(getContainer(), ExpProtocolApplication.class));
        pa.setCpasType(appType.toString());
        pa.setRunId(getRowId());
        pa.setStartTime(startTime);
        pa.setEndTime(endTime);
        pa.setRecordCount(recordCount);
        try
        {
            pa = Table.insert(user, ExperimentServiceImpl.get().getTinfoProtocolApplication(), pa);
            return new ExpProtocolApplicationImpl(pa);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public String getComments()
    {
        return _object.getComments();
    }

    public void setComments(String comments)
    {
        ensureUnlocked();
        _object.setComments(comments);
    }

    public void setEntityId(String entityId)
    {
        ensureUnlocked();
        _object.setEntityId(entityId);
    }

    public String getEntityId()
    {
        return _object.getEntityId();
    }


    public void setContainer(Container container)
    {
        _object.setContainer(container);
    }

    public void setJobId(Integer jobId)
    {
        ensureUnlocked();
        _object.setJobId(jobId);
    }

    public Integer getJobId()
    {
        return _object.getJobId();
    }

    public void setProtocolApplications(ExpProtocolApplicationImpl[] protocolSteps)
    {
        ensureUnlocked();
        _protocolSteps = protocolSteps;
    }

    public Map<ExpMaterial, String> getMaterialInputs()
    {
        ensureFullyPopulated();
        return _materialInputs;
    }

    public Map<ExpData, String> getDataInputs()
    {
        ensureFullyPopulated();
        return _dataInputs;
    }

    public List<ExpMaterial> getMaterialOutputs()
    {
        ensureFullyPopulated();
        return _materialOutputs;
    }

    public List<ExpData> getDataOutputs()
    {
        ensureFullyPopulated();
        return _dataOutputs;
    }

    public ExpProtocolApplicationImpl[] getProtocolApplications()
    {
        ensureFullyPopulated();
        return _protocolSteps;
    }

    public ExpProtocolApplication getInputProtocolApplication()
    {
        return findProtocolApplication(ExpProtocol.ApplicationType.ExperimentRun);
    }

    private ExpProtocolApplication findProtocolApplication(ExpProtocol.ApplicationType type)
    {
        for (ExpProtocolApplicationImpl expProtocolApplication : getProtocolApplications())
        {
            if (type.equals(expProtocolApplication.getApplicationType()))
            {
                return expProtocolApplication;
            }
        }
        return null;
    }

    public ExpProtocolApplication getOutputProtocolApplication()
    {
        return findProtocolApplication(ExpProtocol.ApplicationType.ExperimentRunOutput);
    }

    public void deleteProtocolApplications(User user)
    {
        ensureUnlocked();
        deleteProtocolApplications(getOutputDatas(null), user);
    }

    @Override
    public void setReplacedByRun(ExpRun run)
    {
        ensureUnlocked();
        if (run != null && run.getRowId() < 1)
        {
            throw new IllegalArgumentException("Run must have already been saved to the database");
        }
        _object.setReplacedByRunId(run == null ? null : run.getRowId());
    }

    @Override
    public ExpRun getReplacedByRun()
    {
        Integer id = _object.getReplacedByRunId();
        if (id == null)
        {
            return null;
        }
        if (_replacedByRun == null || _replacedByRun.getRowId() != id.intValue())
        {
            _replacedByRun = ExperimentServiceImpl.get().getExpRun(id.intValue());
        }
        return _replacedByRun;
    }

    @Override
    public List<ExpRunImpl> getReplacesRuns()
    {
        return Arrays.asList(fromRuns(new TableSelector(ExperimentServiceImpl.get().getTinfoExperimentRun(), new SimpleFilter(FieldKey.fromParts("ReplacedByRunId"), getRowId()), new Sort("Name")).getArray(ExperimentRun.class)));
    }

    public void deleteProtocolApplications(ExpData[] datasToDelete, User user)
    {
        try
        {
            if (user == null || !getContainer().hasPermission(user, DeletePermission.class))
            {
                throw new SQLException("Attempting to delete an ExperimentRun without having delete permissions for its container");
            }
            DbCache.remove(ExperimentServiceImpl.get().getTinfoExperimentRun(), ExperimentServiceImpl.get().getCacheKey(getLSID()));

            ExperimentServiceImpl.get().beforeDeleteData(datasToDelete);

            String sql = " ";
            sql += "DELETE FROM exp.ProtocolApplicationParameter WHERE ProtocolApplicationId IN (SELECT RowId FROM exp.ProtocolApplication WHERE RunId = " + getRowId() + ");\n";

            //per Josh: break relation between all datas with this run id and don't delete them
            sql += "UPDATE " + ExperimentServiceImpl.get().getTinfoData() + " SET SourceApplicationId = NULL, RunId = NULL " +
                    " WHERE RunId = " + getRowId() + ";\n";

            sql += "UPDATE " + ExperimentServiceImpl.get().getTinfoMaterial() + " SET SourceApplicationId = NULL, RunId = NULL " +
                    " WHERE RunId = " + getRowId() + ";\n";

            sql += "DELETE FROM exp.DataInput WHERE TargetApplicationId IN (SELECT RowId FROM exp.ProtocolApplication WHERE RunId = " + getRowId() + ");\n";
            sql += "DELETE FROM exp.MaterialInput WHERE TargetApplicationId IN (SELECT RowId FROM exp.ProtocolApplication WHERE RunId = " + getRowId() + ");\n";
            sql += "DELETE FROM exp.DataInput WHERE DataId IN (SELECT RowId FROM exp.Data WHERE RunId = " + getRowId() + ");\n";
            sql += "DELETE FROM exp.MaterialInput WHERE MaterialId IN (SELECT RowId FROM exp.Material WHERE RunId = " + getRowId() + ");\n";

            new SqlExecutor(ExperimentServiceImpl.get().getExpSchema()).execute(sql);

            ExpMaterial[] materialsToDelete = ExperimentServiceImpl.get().getExpMaterialsForRun(getRowId());
            for (ExpMaterial expMaterial : materialsToDelete)
            {
                expMaterial.delete(user);
            }

            new SqlExecutor(ExperimentServiceImpl.get().getExpSchema()).execute("DELETE FROM exp.ProtocolApplication WHERE RunId = " + getRowId());

            ExperimentRunGraph.clearCache(getContainer());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private synchronized void ensureFullyPopulated()
    {
        if (!_populated)
        {
            _populated = true;
            ExperimentServiceImpl.get().populateRun(this);
            Collections.sort(_materialOutputs);
            Collections.sort(_dataOutputs);

            Map<ExpMaterial, String> sortedMaterialInputs = new TreeMap<>();
            sortedMaterialInputs.putAll(_materialInputs);
            _materialInputs = sortedMaterialInputs;

            Map<ExpData, String> sortedDataInputs = new TreeMap<>();
            sortedDataInputs.putAll(_dataInputs);
            _dataInputs = sortedDataInputs;

            for (ExpProtocolApplicationImpl step : _protocolSteps)
            {
                Collections.sort(step.getInputDatas());
                Collections.sort(step.getOutputDatas());
                Collections.sort(step.getInputMaterials());
                Collections.sort(step.getOutputMaterials());
            }
        }
    }

    public void trimRunTree(Integer id, String type) throws ExperimentException
    {
        ensureUnlocked();
        List<ExpProtocolApplication> listPA = new ArrayList<>();
        List<ExpMaterial> listM = new ArrayList<>();
        List<ExpData> listD = new ArrayList<>();
        Set<ExpProtocolApplication> ancestorPAStack = new LinkedHashSet<>();
        Set<ExpProtocolApplication> descendantPAStack = new LinkedHashSet<>();
        ExpProtocolApplicationImpl[] apps = getProtocolApplications();

        boolean found = false;

        // support focus on a starting material that is not part of the run
        if (type.equals(DotGraph.TYPECODE_MATERIAL))
        {
            for (ExpMaterial m : getMaterialInputs().keySet())
                if (m.getRowId() == id.intValue())
                {
                    found = true;
                    listM.add(m);
                    listPA.addAll(m.getSuccessorApps());
                    descendantPAStack.addAll(m.getSuccessorApps());
                    break;
                }
        }
        if (type.equals(DotGraph.TYPECODE_DATA))
        {
            for (ExpData d : getDataInputs().keySet())
                if (d.getRowId() == id.intValue())
                {
                    found = true;
                    listD.add(d);
                    listPA.addAll(d.getSuccessorApps());
                    descendantPAStack.addAll(d.getSuccessorApps());
                    break;
                }
        }
        if (!found)
        {
            for (ExpProtocolApplication app : apps)
            {
                if (type.equals(DotGraph.TYPECODE_MATERIAL))
                {
                    List<ExpMaterial> outputMat = app.getOutputMaterials();
                    for (ExpMaterial m : outputMat)
                        if (m.getRowId() == id.intValue())
                        {
                            found = true;
                            listM.add(m);
                            listPA.addAll(m.getSuccessorApps());
                            descendantPAStack.addAll(m.getSuccessorApps());
                            if (null != m.getSourceApplication() && m.getRun() != null && getRowId() == m.getRun().getRowId())
                            {
                                listPA.add(m.getSourceApplication());
                                ancestorPAStack.add(m.getSourceApplication());
                            }
                            break;
                        }
                }
                if (type.equals(DotGraph.TYPECODE_DATA))
                {
                    for (ExpData d : app.getOutputDatas())
                    {
                        if (d.getRowId() == id.intValue())
                        {
                            found = true;
                            listD.add(d);
                            listPA.addAll(d.getSuccessorApps());
                            descendantPAStack.addAll(d.getSuccessorApps());
                            if (null != d.getSourceApplication() && d.getRun() != null && getRowId() == d.getRun().getRowId())
                            {
                                listPA.add(d.getSourceApplication());
                                ancestorPAStack.add(d.getSourceApplication());
                            }
                            break;
                        }
                    }
                }
                if (type.equals(DotGraph.TYPECODE_PROT_APP))
                {
                    if (app.getRowId() == id.intValue())
                    {
                        found = true;
                        listPA.add(app);
                        ancestorPAStack.add(app);
                        descendantPAStack.add(app);
                        break;
                    }
                }
                if (found)
                    break;
            }
        }
        if (!found)
            throw new ExperimentException("Specified node not found in Experiment Run");

        int loopCount = 0;
        while (descendantPAStack.size() > 0)
        {
            if (loopCount++ > 10000)
            {
                throw new IllegalStateException("Infinite loop detected for run " + getRowId());
            }

            ExpProtocolApplication pa = descendantPAStack.iterator().next();
            for (ExpMaterial m : pa.getOutputMaterials())
            {
                listM.add(m);
                descendantPAStack.addAll(m.getSuccessorApps());
            }
            for (ExpData d : pa.getOutputDatas())
            {
                listD.add(d);
                descendantPAStack.addAll(d.getSuccessorApps());
            }
            descendantPAStack.remove(pa);
            listPA.add(pa);
            if (loopCount++ > 10000)
            {
                throw new IllegalStateException("Infinite loop detected for run " + getRowId());
            }
        }

        loopCount = 0;
        while (ancestorPAStack.size() > 0)
        {
            if (loopCount++ > 10000)
            {
                throw new IllegalStateException("Infinite loop detected for run " + getRowId());
            }

            ExpProtocolApplication pa = ancestorPAStack.iterator().next();
            if (pa.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRun)
                break;
            for (ExpMaterial m : pa.getInputMaterials())
            {
                listM.add(m);
                if (getMaterialInputs().containsKey(m))
                {
                    ExpProtocolApplication runNode = getProtocolApplications()[0];
                    assert runNode.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRun;
                    listPA.add(runNode);
                    continue;
                }
                if (null != m.getSourceApplication() && m.getRun() != null && getRowId() == m.getRun().getRowId())
                    ancestorPAStack.add(m.getSourceApplication());
            }
            for (ExpData d : pa.getInputDatas())
            {
                listD.add(d);
                if (getDataInputs().containsKey(d))
                {
                    ExpProtocolApplication runNode = getProtocolApplications()[0];
                    assert runNode.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRun;
                    listPA.add(runNode);
                    continue;
                }
                if (null != d.getSourceApplication() && d.getRun() != null && getRowId() == d.getRun().getRowId())
                    ancestorPAStack.add(d.getSourceApplication());
            }
            ancestorPAStack.remove(pa);
            listPA.add(pa);
        }

        ArrayList<ExpProtocolApplicationImpl> allPA = new ArrayList<>();
        ArrayList<ExpProtocolApplication> deletePA;
        ArrayList<ExpMaterial> deleteM;
        ArrayList<ExpData> deleteD;

        setProtocolApplications(null);

        for (ExpProtocolApplicationImpl app : apps)
        {
            if (listPA.contains(app))
            {
                allPA.add(app);
                deleteM = new ArrayList<>();
                for (ExpMaterial m : app.getInputMaterials())
                {
                    if (listM.contains(m))
                    {
                        deletePA = new ArrayList<>();
                        for (ExpProtocolApplication p : m.getSuccessorApps())
                            if (!listPA.contains(p))
                                deletePA.add(p);
                        for (ExpProtocolApplication p : deletePA)
                            m.getSuccessorApps().remove(p);
                    }
                    else
                        deleteM.add(m);
                }
                for (ExpMaterial m : deleteM)
                {
                    app.getInputMaterials().remove(m);
                    getMaterialInputs().remove(m);
                }

                deleteD = new ArrayList<>();
                for (ExpData d : app.getInputDatas())
                {
                    if (listD.contains(d))
                    {
                        deletePA = new ArrayList<>();
                        for (ExpProtocolApplication p : d.getSuccessorApps())
                            if (!listPA.contains(p))
                                deletePA.add(p);
                        for (ExpProtocolApplication p : deletePA)
                            d.getSuccessorApps().remove(p);
                    }
                    else
                        deleteD.add(d);
                }
                for (ExpData d : deleteD)
                {
                    app.getInputDatas().remove(d);
                    getDataInputs().remove(d);
                }

                deleteM = new ArrayList<>();
                for (ExpMaterial m : app.getOutputMaterials())
                {
                    if (!listM.contains(m))
                        deleteM.add(m);
                }
                for (ExpMaterial m : deleteM)
                    app.getOutputMaterials().remove(m);


                deleteD = new ArrayList<>();
                for (ExpData d : app.getOutputDatas())
                {
                    if (!listD.contains(d))
                        deleteD.add(d);
                }
                for (ExpData d : deleteD)
                    app.getOutputDatas().remove(d);
            }
        }
        setProtocolApplications(allPA.toArray(new ExpProtocolApplicationImpl[allPA.size()]));
    }

    public ExpDataImpl[] getAllDataUsedByRun()
    {
        SQLFragment sql = new SQLFragment();
        sql.append("select d.* from ");
        sql.append(ExperimentServiceImpl.get().getTinfoDataInput(), "di");
        sql.append(", ");
        sql.append(ExperimentServiceImpl.get().getTinfoData(), "d");
        sql.append(", ");
        sql.append(ExperimentServiceImpl.get().getTinfoProtocolApplication(), "pa");
        sql.append(" where di.targetapplicationid=pa.rowid and pa.runid=? and di.dataid=d.rowid");
        sql.add(getRowId());
        return ExpDataImpl.fromDatas(new SqlSelector(ExperimentServiceImpl.get().getSchema(), sql).getArray(Data.class));
    }


    @Override
    public String getContainerId()
    {
        return _object.getContainer().getId();
    }


    @Override
    public String getDownloadURL(ViewContext context, String name)
    {
        return null;
    }


    public void clearCache()
    {
        _populated = false;
        _dataInputs = new HashMap<>();
        _materialInputs = new HashMap<>();
        _materialOutputs = new ArrayList<>();
        _dataOutputs = new ArrayList<>();
        _protocolSteps = null;
    }

    @Override
    public void archiveDataFiles(User user)
    {
        try
        {
            for (ExpData expData : getAllDataUsedByRun())
            {
                File file = expData.getFile();
                // If we can find the file on disk, and it's in the assaydata directory, and there aren't any other
                // usages, move it into an archived subdirectory
                if (file != null && NetworkDrive.exists(file) && file.isFile() &&
                    file.getParentFile().equals(AssayFileWriter.ensureUploadDirectory( getContainer())) && !hasOtherRunUsing(expData, this))
                {
                    File archivedDir = null;
                    try
                    {
                        archivedDir = AssayFileWriter.ensureSubdirectory(getContainer(), AssayFileWriter.ARCHIVED_DIR_NAME);
                    }
                    catch (ExperimentException e)
                    {
                        // In this case, the archived directory was not created correctly perhaps due to file permission or
                        // some other problem. This exception has been observed on production servers. The error is being
                        // ignored and the file is not archived. Archiving is a best attempt action.
                        LOG.warn("Unable to create an archive directory - discontinue archive and delete.");
                        return;
                    }
                    File targetFile = AssayFileWriter.findUniqueFileName(file.getName(), archivedDir);
                    targetFile = FileUtil.getAbsoluteCaseSensitiveFile(targetFile);
                    FileUtils.moveFile(file, targetFile);
                    expData.setDataFileURI(targetFile.toURI());
                    expData.save(user);
                }
            }
        }
        // Exceptions were not being handled by calling functions so we translate them here
        catch (ExperimentException e)
        {
            throw UnexpectedException.wrap(e);
        }
        catch (IOException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    private boolean hasOtherRunUsing(ExpData originalData, ExpRun originalRun)
    {
        if (originalData.getDataFileUrl() != null)
        {
            for (ExpData data : ExperimentService.get().getAllExpDataByURL(originalData.getDataFileUrl()))
            {
                for (ExpRun run : data.getTargetRuns())
                {
                    if (!run.equals(originalRun))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
