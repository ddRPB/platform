/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.exp.property;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.ActionURL;

import java.util.Map;
import java.util.Set;

abstract public class DomainKind
{
    abstract public String getTypeLabel(Domain domain);
    abstract public boolean isDomainType(String domainURI);
    abstract public SQLFragment sqlObjectIdsInDomain(Domain domain);

//    abstract public String generateDomainURI(Container container, String name);

    abstract public Map.Entry<TableInfo, ColumnInfo> getTableInfo(User user, Domain domain, Container[] containers);
    abstract public ActionURL urlShowData(Domain domain);
    abstract public ActionURL urlEditDefinition(Domain domain);
    
    public boolean canEditDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, AdminPermission.class);
    }

    // Do any special handling before a PropertyDescriptor is deleted -- do nothing by default
    public void deletePropertyDescriptor(Domain domain, User user, PropertyDescriptor pd)
    {
    }

    /**
     * Return the set of names that should not be allowed for properties. E.g.
     * the names of columns from the hard table underlying this type
     * @return set of strings containing the names. This will be compared ignoring case
     */
    abstract public Set<String> getReservedPropertyNames(Domain domain);

    // CONSIDER: have DomainKind supply and IDomainInstance or similiar
    // so that it can hold instance data (e.g. a DatasetDefinition)
}
