package org.labkey.announcements.api;

import org.json.JSONObject;
import org.labkey.announcements.model.TourModel;
import org.labkey.api.announcements.api.Tour;

/**
 * Created by Marty on 1/15/2015.
 */
public class TourImpl implements Tour
{
    TourModel _model;

    public TourImpl(TourModel model)
    {
        _model = model;
    }

    public Integer getRowId()
    {
        return _model.getRowId();
    }

    public void setRowId(Integer id)
    {
        _model.setRowId(id);
    }

    public String getTitle()
    {
        return _model.getTitle();
    }

    public void setTitle(String title)
    {
        _model.setTitle(title);
    }

    public String getDescription()
    {
        return _model.getDescription();
    }

    public void setDescription(String description)
    {
        _model.setDescription(description);
    }

    public String getJson()
    {
        return _model.getJson();
    }

    public void setJson(String json)
    {
        _model.setJson(json);
    }

    public Integer getMode()
    {
        return _model.getMode();
    }

    public void setMode(Integer mode)
    {
        _model.setMode(mode);
    }

    public JSONObject toJSON()
    {
        return _model.toJSON();
    }
}
