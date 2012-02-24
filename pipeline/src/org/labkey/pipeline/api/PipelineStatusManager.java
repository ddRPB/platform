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

package org.labkey.pipeline.api;

import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.pipeline.*;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewBackgroundInfo;

import java.sql.SQLException;
import java.util.*;

/**
 * <code>PipelineStatusManager</code> provides access to the StatusFiles table
 * in the Pipeline schema for recording status on <code>PipelineJob</code> automated
 * analysis.
 * 
 * @author brendanx
 */
public class PipelineStatusManager
{
    private static PipelineSchema _schema = PipelineSchema.getInstance();
    private static Logger _log = Logger.getLogger(PipelineStatusManager.class);

    public static TableInfo getTableInfo()
    {
        return _schema.getTableInfoStatusFiles();
    }

    /**
     * Get a <code>PipelineStatusFileImpl</code> by RowId in the database.
     *
     * @param rowId id field
     * @return the corresponding <code>PipelineStatusFileImpl</code>
     */
    public static PipelineStatusFileImpl getStatusFile(int rowId)
    {
        try
        {
            return getStatusFile(new SimpleFilter("RowId", rowId));
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    /**
     * Get a <code>PipelineStatusFileImpl</code> by the file path associated with the
     * entry.
     *
     * @param path file path to for the associated file
     * @return the corresponding <code>PipelineStatusFileImpl</code>
     * @throws SQLException database error
     */
    public static PipelineStatusFileImpl getStatusFile(String path) throws SQLException
    {
        return (path == null ? null :
                getStatusFile(new SimpleFilter("FilePath", PipelineJobService.statusPathOf(path))));
    }

    /**
     * Get a <code>PipelineStatusFileImpl</code> by the Job's id.
     *
     * @param jobId the job id for the associated job
     * @return the corresponding <code>PipelineStatusFileImpl</code>
     * @throws SQLException database error
     */
    public static PipelineStatusFileImpl getJobStatusFile(String jobId) throws SQLException
    {
        return (jobId == null ? null :
                getStatusFile(new SimpleFilter("Job", jobId)));
    }

    /**
     * Get a <code>PipelineStatusFileImpl</code> by the Job's row id.
     *
     * @param rowId the row id for the associated job
     * @return the corresponding <code>PipelineStatusFileImpl</code>
     * @throws SQLException database error
     */
    public static PipelineStatusFileImpl getJobStatusFile(int rowId) throws SQLException
    {
        return getStatusFile(new SimpleFilter("RowId", rowId));
    }

    /**
     * Get a <code>PipelineStatusFileImpl</code> using a specific filter.
     *
     * @param filter the filter to use in the select statement
     * @return the corresponding <code>PipelineStatusFileImpl</code>
     * @throws SQLException database error
     */
    private static PipelineStatusFileImpl getStatusFile(Filter filter) throws SQLException
    {
        PipelineStatusFileImpl[] asf =
                Table.select(_schema.getTableInfoStatusFiles(), Table.ALL_COLUMNS, filter, null, PipelineStatusFileImpl.class);

        if (asf.length == 0)
            return null;

        return asf[0];
    }

    public static boolean setStatusFile(PipelineJob job, User user, String status, String info, boolean allowInsert)
    {
        try
        {
            PipelineStatusFileImpl sfExist = getJobStatusFile(job.getJobGUID());
            if (sfExist == null)
            {
                // Then try based on file path
                sfExist = getStatusFile(job.getLogFile().toString());
            }
            PipelineStatusFileImpl sfSet = new PipelineStatusFileImpl(job, status, info);

            if (null == sfExist)
            {
                if (allowInsert)
                {
                    sfSet.beforeInsert(user, job.getContainerId());
                    PipelineStatusFileImpl sfNew = Table.insert(user, _schema.getTableInfoStatusFiles(), sfSet);

                    // Make sure rowID is correct, since it might be used in email.
                    sfSet.setRowId(sfNew.getRowId());
                }
                else
                {
                    job.getLogger().error("Could not find job in database for job GUID " + job.getJobGUID() + ", unable to set its status to '" + status + "'");
                    return false;
                }
            }
            else
            {
                boolean cancelled = false;
                if (PipelineJob.CANCELLING_STATUS.equals(sfExist.getStatus()) && sfSet.isActive())
                {
                    // Mark is as officially dead
                    sfSet.setStatus(PipelineJob.CANCELLED_STATUS);
                    cancelled = true;
                }
                sfSet.beforeUpdate(user, sfExist);
                updateStatusFile(sfSet);
                if (cancelled)
                {
                    // Signal to the caller that the job shouldn't move on to its next state
                    throw new CancelledException();
                }
            }

            if (isNotifyOnError(job) && PipelineJob.ERROR_STATUS.equals(sfSet.getStatus()) &&
                    (sfExist == null || !PipelineJob.ERROR_STATUS.equals(sfExist.getStatus())))
            {
                _log.info("Error status has changed - considering an email notification");
                PipelineManager.sendNotificationEmail(sfSet, job.getContainer());
            }

            if (PipelineJob.ERROR_STATUS.equals(status))
            {
                // Count this error on the job.
                job.setErrors(job.getErrors() + 1);
            }
            else if (PipelineJob.COMPLETE_STATUS.equals(status))
            {
                // Make sure the Enterprise Pipeline recognizes this as a completed
                // job, even if did it not have a TaskPipeline.
                job.setActiveTaskId(null, false);

                // Notify if this is not a split job
                if (job.getParentGUID() == null)
                    PipelineManager.sendNotificationEmail(sfSet, job.getContainer());
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        return true;
    }

    private static boolean isNotifyOnError(PipelineJob job)
    {
        return !job.isAutoRetry();
    }

    /**
     * Update status on a status file read from the database.
     *
     * @param sf the modified status
     * @throws SQLException database error
     */
    public static void updateStatusFile(PipelineStatusFileImpl sf) throws SQLException
    {
        DbScope scope = PipelineSchema.getInstance().getSchema().getScope();
        boolean active = scope.isTransactionActive();
        try
        {
            beginTransaction(scope,active);
            enforceLockOrder(sf.getJob(), active);

            Table.update(null, _schema.getTableInfoStatusFiles(), sf, sf.getRowId());

            commitTransaction(scope, active);
        }
        finally
        {
            closeTransaction(scope, active);
        }
    }

    /**
     * If there is an existing status entry for this file, make sure it has the
     * right job GUID, updating children as needed
     */
    public static void resetJobId(String path, String jobId)
    {
        DbScope scope = PipelineSchema.getInstance().getSchema().getScope();
        boolean active = scope.isTransactionActive();
        try
        {
            beginTransaction(scope, active);
            enforceLockOrder(jobId, active);

            PipelineStatusFileImpl sfExist = getStatusFile(path);
            if (sfExist != null)
            {
                PipelineStatusFileImpl[] children = getSplitStatusFiles(sfExist.getJobId(), ContainerManager.getForId(sfExist.getContainerId()));
                for (PipelineStatusFileImpl child : children)
                {
                    child.setJobParent(null);
                    child.beforeUpdate(null, child);
                    enforceLockOrder(child.getJobId(), active);
                    updateStatusFile(child);
                }
                sfExist.setJob(jobId);
                sfExist.beforeUpdate(null, sfExist);
                updateStatusFile(sfExist);
                for (PipelineStatusFileImpl child : children)
                {
                    child.setJobParent(jobId);
                    child.beforeUpdate(null, child);
                    updateStatusFile(child);
                }
            }
            commitTransaction(scope, active);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            closeTransaction(scope, active);
        }
    }

    public static void ensureError(PipelineJob job) throws Exception
    {
        PipelineStatusFileImpl sfExist = getJobStatusFile(job.getJobGUID());
        if (sfExist == null)
            throw new SQLException("Status for the job " + job.getJobGUID() + " was not found.");

        if (!PipelineJob.ERROR_STATUS.equals(sfExist.getStatus()))
        {
            setStatusFile(job, job.getUser(), PipelineJob.ERROR_STATUS, null, false);
        }
    }

    public static void storeJob(String jobId, String xml) throws SQLException
    {
        PipelineStatusFileImpl sfExist = getJobStatusFile(jobId);
        if (sfExist == null)
            throw new SQLException("Status for the job " + jobId + " was not found.");

        StringBuffer sql = new StringBuffer();
        sql.append("UPDATE ").append(_schema.getTableInfoStatusFiles())
                .append(" SET JobStore = ?")
                .append(" WHERE RowId = ?");

        Table.execute(_schema.getSchema(), sql.toString(), xml, sfExist.getRowId());
    }

    public static String retrieveJob(int rowId) throws SQLException
    {
        PipelineStatusFileImpl sfExist = getJobStatusFile(rowId);
        if (sfExist == null)
            return null;

        return retrieveJob(sfExist);
    }

    public static String retrieveJob(String jobId) throws SQLException
    {
        PipelineStatusFileImpl sfExist = getJobStatusFile(jobId);
        if (sfExist == null)
            throw new SQLException("Status for the job " + jobId + " was not found.");
        
        return retrieveJob(sfExist);
    }

    private static String retrieveJob(PipelineStatusFileImpl sfExist) throws SQLException
    {
        StringBuffer sql = new StringBuffer();
        sql.append("UPDATE ").append(_schema.getTableInfoStatusFiles())
                .append(" SET JobStore = NULL")
                .append(" WHERE RowId = ?");

        Table.execute(_schema.getSchema(), sql.toString(), sfExist.getRowId());

        return sfExist.getJobStore();
    }

    public static PipelineStatusFileImpl[] getSplitStatusFiles(String parentId, Container container)
    {
        if (parentId == null)
        {
            return new PipelineStatusFileImpl[0];
        }
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("JobParent", parentId, CompareType.EQUAL);
        if (null != container)
            filter.addCondition("Container", container.getId(), CompareType.EQUAL);

        try
        {
            return Table.select(_schema.getTableInfoStatusFiles(), Table.ALL_COLUMNS, filter, null, PipelineStatusFileImpl.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    /**
    * Returns a count of jobs not marked COMPLETE which were created by splitting another job.
    *
    * @param parentId the jobGUID for the joined task that created split tasks
    * @param container the container where the joined task is defined
    * @return int count of <code>PipelineStatusFiles<code> not marked COMPLETE
    * @throws SQLException database error
    */
    public static int getIncompleteStatusFileCount(String parentId, Container container) throws SQLException
    {
        return Table.executeSingleton(_schema.getSchema(),
            "SELECT COUNT(*) FROM " + _schema.getTableInfoStatusFiles() +  " WHERE Container = ? AND JobParent = ? AND Status <> ? ",
            new Object[]{container.getId(), parentId,PipelineJob.COMPLETE_STATUS }, Integer.class);
    }

    public static List<PipelineStatusFileImpl> getStatusFilesForLocation(String location, boolean includeJobsOnQueue)
    {
        // NOTE: JobIds end up all uppercase in the database, but they are lowercase in jobs
        Set<String> ignorableIds = new CaseInsensitiveHashSet();
        if (!includeJobsOnQueue)
        {
            List<PipelineJob> queuedJobs = PipelineService.get().getPipelineQueue().findJobs(location);
            for (PipelineJob job : queuedJobs)
            {
                ignorableIds.add(job.getJobGUID());
            }
        }

        List<PipelineStatusFileImpl> result = new ArrayList<PipelineStatusFileImpl>();
        TaskPipelineRegistry registry = PipelineJobService.get();
        for (TaskFactory taskFactory : registry.getTaskFactories())
        {
            if (taskFactory.getExecutionLocation().equals(location))
            {
                TaskId id = taskFactory.getId();
                PipelineStatusFileImpl[] statusFiles = getQueuedStatusFilesForActiveTaskId(id.toString());
                for (PipelineStatusFileImpl statusFile : statusFiles)
                {
                    if (!ignorableIds.contains(statusFile.getJobId()))
                    {
                        result.add(statusFile);
                    }
                }
            }
        }
        return result;
    }

    public static PipelineStatusFileImpl[] getQueuedStatusFilesForActiveTaskId(String activeTaskId)
    {
        try
        {
            SimpleFilter filter = createQueueFilter();
            filter.addCondition("ActiveTaskId", activeTaskId, CompareType.EQUAL);

            return Table.select(_schema.getTableInfoStatusFiles(), Table.ALL_COLUMNS, filter, null, PipelineStatusFileImpl.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public static PipelineStatusFile[] getQueuedStatusFilesForContainer(Container c) throws SQLException
    {
        SimpleFilter filter = createQueueFilter();
        filter.addCondition("Container", c.getId(), CompareType.EQUAL);

        return Table.select(_schema.getTableInfoStatusFiles(), Table.ALL_COLUMNS, filter, null, PipelineStatusFileImpl.class);
    }

    public static PipelineStatusFile[] getJobsWaitingForFiles(Container c)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter("Status", PipelineJob.WAITING_FOR_FILES);
            filter.addCondition("Container", c.getId(), CompareType.EQUAL);

            return Table.select(_schema.getTableInfoStatusFiles(), Table.ALL_COLUMNS, filter, null, PipelineStatusFileImpl.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public static PipelineStatusFileImpl[] getQueuedStatusFiles() throws SQLException
    {
        SimpleFilter filter = createQueueFilter();
        
        return Table.select(_schema.getTableInfoStatusFiles(), Table.ALL_COLUMNS, filter, null, PipelineStatusFileImpl.class);
    }

    private static SimpleFilter createQueueFilter()
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("Status", PipelineJob.COMPLETE_STATUS, CompareType.NEQ);
        filter.addCondition("Status", PipelineJob.ERROR_STATUS, CompareType.NEQ);
        filter.addCondition("Status", PipelineJob.WAITING_FOR_FILES, CompareType.NEQ);
        filter.addCondition("Status", PipelineJob.SPLIT_STATUS, CompareType.NEQ);
        filter.addCondition("Status", PipelineJob.CANCELLED_STATUS, CompareType.NEQ);
        filter.addCondition("Job", null, CompareType.NONBLANK);
        return filter;
    }

    public static void completeStatus(User user, int... rowIds) throws SQLException, Container.ContainerException
    {
        PipelineSchema.getInstance().getSchema().getScope().ensureTransaction();
        try
        {
            for (int rowId : rowIds)
            {
                boolean statusSet = false;
                try
                {
                    PipelineJob job = PipelineJobService.get().getJobStore().getJob(rowId);

                    if (job != null)
                    {
                        job.info("Job " + job + " was marked as complete by " + user);
                        if (!job.getContainer().hasPermission(user, UpdatePermission.class))
                        {
                            throw new UnauthorizedException();
                        }

                        setStatusFile(job, user, PipelineJob.COMPLETE_STATUS, null, false);
                        statusSet = true;
                    }
                }
                catch (Exception e)
                {
                    _log.error("Failed to get pipeline job", e);
                }

                if (!statusSet)
                {
                    // Fall back to updating the simple bean in the case where can can't deserialize the job itself
                    PipelineStatusFileImpl sf = PipelineStatusManager.getStatusFile(rowId);
                    if (sf != null)
                    {
                        _log.info("Job " + sf.getFilePath() + " was marked as complete by " + user);
                        sf.setStatus(PipelineJob.COMPLETE_STATUS);
                        sf.setInfo(null);
                        PipelineStatusManager.updateStatusFile(sf);
                    }
                }
            }
            PipelineSchema.getInstance().getSchema().getScope().commitTransaction();
        }
        finally
        {
            PipelineSchema.getInstance().getSchema().getScope().closeConnection();
        }
    }

    public static void deleteStatus(ViewBackgroundInfo info, int... rowIds) throws SQLException
    {
        if (rowIds.length == 0)
        {
            return;
        }

        ArrayList<PipelineStatusFile> deleteable = new ArrayList<PipelineStatusFile>();
        for (int rowId : rowIds)
        {
            PipelineStatusFile sf = getStatusFile(rowId);

            // First check that it still exists in the database and that it isn't running anymore
            if (sf != null && !sf.isActive())
            {
                Container c = sf.lookupContainer();
                if (!c.hasPermission(info.getUser(), DeletePermission.class))
                {
                    throw new UnauthorizedException();
                }
                // Check if the job has any children
                PipelineStatusFileImpl[] children = PipelineStatusManager.getSplitStatusFiles(sf.getJobId(), sf.lookupContainer());
                boolean hasActiveChildren = false;
                for (PipelineStatusFileImpl child : children)
                {
                    hasActiveChildren |= child.isActive();
                }

                if (!hasActiveChildren)
                {
                    if (children.length == 0)
                    {
                        deleteable.add(sf);
                    }
                    else
                    {
                        // Delete the children first and let the recursion delete the parent.
                        deleteable.addAll(Arrays.asList(children));
                    }
                }
            }
        }

        if (!deleteable.isEmpty())
        {
            Container c = info.getContainer();
            StringBuffer sql = new StringBuffer();
            sql.append("DELETE FROM ").append(_schema.getTableInfoStatusFiles())
                    .append(" ").append("WHERE RowId IN (");

            String separator = "";
            List<Object> params = new ArrayList<Object>();
            for (PipelineStatusFile pipelineStatusFile : deleteable)
            {
                // Allow the provider to do any necessary clean-up
                PipelineProvider provider = PipelineService.get().getPipelineProvider(pipelineStatusFile.getProvider());
                if (provider != null)
                    provider.preDeleteStatusFile(pipelineStatusFile);

                sql.append(separator);
                separator = ", ";
                sql.append("?");
                _log.info("Job " + pipelineStatusFile.getFilePath() + " was deleted by " + info.getUser());
                params.add(pipelineStatusFile.getRowId());
            }
            sql.append(")");

            if (!c.isRoot())
            {
                sql.append(" AND Container = ?");
                params.add(c.getId());
            }
            Table.execute(_schema.getSchema(), sql.toString(), params.toArray());

            // If we deleted anything, try recursing since we may have deleted all the child jobs which would
            // allow a parent job to be deleted
            deleteStatus(info, rowIds);
        }
    }

    public static void cancelStatus(ViewBackgroundInfo info, int... rowIds) throws SQLException
    {
        if (rowIds.length == 0)
        {
            return;
        }

        for (int rowId : rowIds)
        {
            PipelineStatusFileImpl statusFile = PipelineStatusManager.getStatusFile(rowId);
            if (statusFile == null)
            {
                throw new NotFoundException();
            }
            cancelStatus(info, statusFile);
        }
    }

    private static void cancelStatus(ViewBackgroundInfo info, PipelineStatusFileImpl statusFile)
            throws SQLException
    {
        Container jobContainer = statusFile.lookupContainer();
        if (!jobContainer.hasPermission(info.getUser(), DeletePermission.class))
        {
            throw new UnauthorizedException();
        }
        if (statusFile.isCancellable())
        {
            PipelineStatusFileImpl[] children = PipelineStatusManager.getSplitStatusFiles(statusFile.getJobId(), statusFile.lookupContainer());
            for (PipelineStatusFileImpl child : children)
            {
                if (child.isCancellable())
                {
                    cancelStatus(info, child);
                }
            }

            String newStatus;
            if (PipelineJob.SPLIT_STATUS.equals(statusFile.getStatus()) || PipelineJob.WAITING_FOR_FILES.equals(statusFile.getStatus()))
            {
                newStatus = PipelineJob.CANCELLED_STATUS;
            }
            else
            {
                newStatus = PipelineJob.CANCELLING_STATUS;
            }
            statusFile.setStatus(newStatus);
            PipelineStatusManager.updateStatusFile(statusFile);
            PipelineService.get().getPipelineQueue().cancelJob(jobContainer, statusFile);
        }
    }

    /**
    * starts a transaction for a pipeline status job.
    *
    * @param scope the dbScope that has the transaction context
    * @param active a boolean the caller tests that says whether a transaction is already active/
    * @throws SQLException database error
    */
    protected static void beginTransaction(DbScope scope, boolean active) throws SQLException
    {
        if (!active)
        {
            scope.beginTransaction();
        }
    }

    /**
    * commits a transaction for a pipeline status job.
    *
    * @param scope the dbScope that has the transaction context
    * @param active a boolean the caller tests that says whether a transaction is already active/
    * @throws SQLException database error
    */
    protected static void commitTransaction(DbScope scope, boolean active) throws SQLException
    {
        if (!active)
            scope.commitTransaction();
    }

    /**
    * closes a transaction for a pipeline status job.  goes in the finally block
     * of the caller.
    *
    * @param scope the dbScope that has the transaction context
    * @param active a boolean the caller tests that says whether a transaction is already active/
    */
    protected static void closeTransaction(DbScope scope, boolean active)
    {
        if (!active)
        {
            if(scope.isTransactionActive())
               scope.closeConnection();
         }
    }

    /**
    *  grabs shared locks on the index pages of the secondary indexes for the job about to be updated.
     * NO-op if the database is not SQL Server.  In SQL Server 2000 and possibly later versions,
     * the 3 different unique keys to a pipeline stastus file can cause reader - writer deadlocks when a
     * writer's change causes an update to an index that a reader is accessing.  The SQL lock hints
     * used here grab exclusive locks on these index keys at the start of the transaction, preventing
     * readers from getting a share lock and ensuring the updater can update the index if necessary.
    *
    * @param jobId of the job that is going to be updated
    * @throws SQLException database error
    */
    protected static void enforceLockOrder(String jobId, boolean active)
            throws SQLException
    {
        if (active)
            return;

        if (!_schema.getSchema().getSqlDialect().isSqlServer())
            return;

        if (null != jobId)
        {
            String lockCmd = "SELECT Job, JobParent, Container FROM " + _schema.getTableInfoStatusFiles() +  " WITH (TABLOCKX) WHERE Job = ?;";
            Table.execute(_schema.getSchema(), lockCmd, jobId) ;
        }
    }
}
