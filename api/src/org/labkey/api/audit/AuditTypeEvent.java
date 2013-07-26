package org.labkey.api.audit;

import org.labkey.api.security.User;

import java.util.Date;

/**
 * User: klum
 * Date: 7/12/13
 */
public class AuditTypeEvent
{
    private int _rowId;
    private Integer _impersonatedBy;
    private String _entityId;
    private String _comment;
    private String _projectId;
    private String _container;
    private String _eventType;
    private Date _created;
    private User _createdBy;
    private Date _modified;
    private User _modifiedBy;

    public AuditTypeEvent(String eventType, String container, String comment)
    {
        _eventType = eventType;
        _container = container;
        _comment = comment;
    }

    public AuditTypeEvent(){}

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public Integer getImpersonatedBy()
    {
        return _impersonatedBy;
    }

    public void setImpersonatedBy(Integer impersonatedBy)
    {
        _impersonatedBy = impersonatedBy;
    }

    public String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(String entityId)
    {
        _entityId = entityId;
    }

    public String getComment()
    {
        return _comment;
    }

    public void setComment(String comment)
    {
        _comment = comment;
    }

    public String getProjectId()
    {
        return _projectId;
    }

    public void setProjectId(String projectId)
    {
        _projectId = projectId;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public String getEventType()
    {
        return _eventType;
    }

    public void setEventType(String eventType)
    {
        _eventType = eventType;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public User getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(User createdBy)
    {
        _createdBy = createdBy;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public User getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(User modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }
}
