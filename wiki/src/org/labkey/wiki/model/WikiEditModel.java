/*
 * Copyright (c) 2007 LabKey Software Foundation
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
package org.labkey.wiki.model;

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;
import org.labkey.api.security.User;
import org.labkey.api.security.ACL;
import org.labkey.wiki.ServiceImpl;
import org.labkey.wiki.WikiManager;
import org.labkey.wiki.WikiController;
import org.labkey.wiki.BaseWikiPermissions;

import java.util.List;

/**
 * Model bean for the wikiEdit.jsp view
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Mar 17, 2008
 * Time: 2:25:41 PM
 */
public class WikiEditModel
{
    private Wiki _wiki;
    private WikiVersion _wikiVersion;
    private String _redir;
    private Container _container;
    private String _format;
    private String _defName;
    private boolean _useVisualEditor = false;
    private String _pageId;
    private int _index;
    private User _user;

    public WikiEditModel(Container container, Wiki wiki, WikiVersion wikiVersion, String redir,
                         String format, String defName, boolean useVisualEditor,
                         String pageId, int index, User user)
    {
        _container = container;
        _wiki = wiki;
        _wikiVersion = wikiVersion;
        _redir = redir;
        _format = format;
        _defName = defName;
        _useVisualEditor = useVisualEditor;
        _pageId = pageId;
        _index = index;
        _user = user;
    }
    
    public Wiki getWiki()
    {
        return _wiki;
    }

    public void setWiki(Wiki wiki)
    {
        _wiki = wiki;
    }

    public WikiVersion getWikiVersion()
    {
        return _wikiVersion;
    }

    public void setWikiVersion(WikiVersion wikiVersion)
    {
        _wikiVersion = wikiVersion;
    }

    public String getRedir()
    {
        return PageFlowUtil.jsString(_redir);
    }

    public void setRedir(String redir)
    {
        _redir = redir;
    }

    public String getBody()
    {
        return null == _wikiVersion || null  == _wikiVersion.getBody() ? "null" : PageFlowUtil.jsString(_wikiVersion.getBody());
    }

    public String getName()
    {
        return null == _wiki ? getDefName() : PageFlowUtil.jsString(_wiki.getName());
    }

    public String getTitle()
    {
        return null == _wikiVersion || null == _wikiVersion.getTitle() ? "null" : PageFlowUtil.jsString(_wikiVersion.getTitle());
    }

    public String getEntityId()
    {
        return null == _wiki ? "null" : "'" + _wiki.getEntityId() + "'";
    }

    public String getRowId()
    {
        return null == _wiki ? "null" : String.valueOf(_wiki.getRowId());
    }

    public int getParent()
    {
        return null == _wiki ? -1 : _wiki.getParent();
    }

    public String getRendererType()
    {
        if(null == _wikiVersion)
            return _format != null ? PageFlowUtil.jsString(_format)
                    : PageFlowUtil.jsString(ServiceImpl.get().getDefaultWikiRendererType().name());
        else
            return PageFlowUtil.jsString(_wikiVersion.getRendererType());
    }

    public boolean isCurrentRendererType(WikiRendererType type)
    {
        if(null == _wikiVersion)
            return ServiceImpl.get().getDefaultWikiRendererType() == type;
        else
            return type.name().equalsIgnoreCase(_wikiVersion.getRendererType());
    }

    public List<Wiki> getPossibleParents()
    {
        List<Wiki> parents = WikiManager.getPageList(_container);

        //remove the current wiki from the list
        //so that it can't become its own parent
        if(null != _wiki)
        {
            for(Wiki wiki : parents)
            {
                if(wiki.getRowId() == _wiki.getRowId())
                {
                    parents.remove(wiki);
                    break;
                }
            }
        }
        
        return parents;
    }

    public boolean hasAttachments()
    {
        return null != _wiki && null != _wiki.getAttachments() && _wiki.getAttachments().size() > 0;
    }

    public String getDefName()
    {
        return null == _defName ? "null" : PageFlowUtil.jsString(_defName);
    }

    public boolean useVisualEditor()
    {
        return _useVisualEditor;
    }

    public String getPageId()
    {
        return null == _pageId ? "null" : PageFlowUtil.jsString(_pageId);
    }

    public int getIndex()
    {
        return _index;
    }

    public boolean canUserDelete()
    {
        if(null == _wiki)
            return _container.hasPermission(_user, ACL.PERM_DELETE);
        else
        {
            BaseWikiPermissions perms = new BaseWikiPermissions(_user, _container);
            return perms.allowDelete(_wiki);
        }
    }
}
