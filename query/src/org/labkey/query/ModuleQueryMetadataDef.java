/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.query;

import org.labkey.api.data.Container;
import org.labkey.api.resource.Resource;
import org.labkey.api.resource.ResourceRef;
import org.labkey.api.util.DOMUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;

/**
 * User: jeckels
 * Date: May 12, 2010
 */
public class ModuleQueryMetadataDef extends ResourceRef
{
    private String _name;
    private String _queryMetaData;
    private String _description;
    private double _schemaVersion;
    private boolean _hidden = false;

    public ModuleQueryMetadataDef(Resource resource)
    {
        super(resource);
        _name = resource.getName();
        if (_name.endsWith(ModuleQueryDef.META_FILE_EXTENSION))
        {
            _name = _name.substring(0, _name.length() - ModuleQueryDef.META_FILE_EXTENSION.length());
        }

        try
        {
            Document doc = parseFile(resource);
            Node docElem = doc.getDocumentElement();

            if (!docElem.getNodeName().equalsIgnoreCase("query"))
                return;

            _name = DOMUtil.getAttributeValue(docElem, "name", _name);
            _hidden = Boolean.parseBoolean(DOMUtil.getAttributeValue(docElem, "hidden", "false"));
            _schemaVersion = Double.parseDouble(DOMUtil.getAttributeValue(docElem, "schemaVersion", "0"));

            //description
            Node node = DOMUtil.getFirstChildNodeWithName(docElem, "description");
            if (null != node)
                _description = DOMUtil.getNodeText(node);

            node = DOMUtil.getFirstChildNodeWithName(docElem, "metadata");
            if (null != node)
            {
                Node root = DOMUtil.getFirstChildElement(node);
                if (null != root)
                    _queryMetaData = PageFlowUtil.convertNodeToXml(root);
            }
        }
        catch (IOException e)
        {
            _log.warn("Unable to load meta-data from module query file " + resource.getPath(), e);
        }
        catch (TransformerException e)
        {
            _log.warn("Unable to load meta-data from module query file " + resource.getPath(), e);
        }
        catch (ParserConfigurationException e)
        {
            _log.warn("Unable to load meta-data from module query file " + resource.getPath(), e);
        }
        catch (SAXException e)
        {
            _log.warn("Unable to load meta-data from module query file " + resource.getPath(), e);
        }
    }

    protected Document parseFile(Resource r) throws ParserConfigurationException, IOException, SAXException
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        DocumentBuilder db = dbf.newDocumentBuilder();

        return db.parse(r.getInputStream());
    }

    public String getName()
    {
        return _name;
    }

    public String getQueryMetaData()
    {
        return _queryMetaData;
    }

    public String getDescription()
    {
        return _description;
    }

    public double getSchemaVersion()
    {
        return _schemaVersion;
    }

    public boolean isHidden()
    {
        return _hidden;
    }

    public QueryDef toQueryDef(Container container)
    {
        QueryDef ret = new QueryDef();
        ret.setContainer(container.getId());
        ret.setName(getName());
        ret.setDescription(getDescription());
        ret.setSchemaVersion(getSchemaVersion());
        ret.setMetaData(getQueryMetaData());
        if(isHidden())
            ret.setFlags(QueryManager.FLAG_HIDDEN);

        return ret;
    }
}
