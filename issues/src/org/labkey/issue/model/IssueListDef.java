package org.labkey.issue.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Entity;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;
import org.labkey.issue.query.IssueDefDomainKind;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 4/5/2016.
 */
public class IssueListDef extends Entity
{
    private int _rowId;
    private String _name;
    private String _label;
    private Container _domainContainer;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    @Nullable
    public TableInfo createTable(User user)
    {
        Domain d = getDomain(user);
        if (d != null)
        {
            return StorageProvisioner.createTableInfo(d);
        }
        return null;
    }

    @Nullable
    public Container getDomainContainer(User user)
    {
        if (_domainContainer == null)
        {
            String id = getContainerId();
            if (id != null)
            {
                Container container = ContainerManager.getForId(id);
                if (container != null)
                {
                    Domain domain = findExistingDomain(container, user);

                    // if a domain already existing for this definition, return the domain container, else
                    // create the domain in the current container
                    if (domain != null)
                    {
                        _domainContainer = domain.getContainer();
                    }
                    else
                    {
                        _domainContainer = container;
                    }
                }
            }
        }
        return _domainContainer;
    }

    public Domain getDomain(User user)
    {
        String uri = generateDomainURI(getDomainContainer(user), user);
        return PropertyService.get().getDomain(getDomainContainer(user), uri);
    }

    private String generateDomainURI(Container c, User user)
    {
        DomainKind domainKind = PropertyService.get().getDomainKindByName(IssueDefDomainKind.NAME);
        return domainKind.generateDomainURI(IssuesSchema.getInstance().getSchemaName(), getName(), c, user);
    }

    public boolean isNew()
    {
        return _rowId == 0;
    }

    public IssueListDef save(User user)
    {
        IssueListDef def = null;

        if (isNew())
        {
            // need to transact this
            def = Table.insert(user, IssuesSchema.getInstance().getTableInfoIssueListDef(), this);
            String uri = generateDomainURI(getDomainContainer(user), user);
            Container domainContainer = getDomainContainer(user);

            Domain domain = PropertyService.get().getDomain(domainContainer, uri);
            if (domain == null)
            {
                try
                {
                    IssueDefDomainKind domainKind = (IssueDefDomainKind)PropertyService.get().getDomainKindByName(IssueDefDomainKind.NAME);
                    domainKind.createLookupDomains(domainContainer, user, getName());
                    domain = PropertyService.get().createDomain(getDomainContainer(user), uri, getName());

                    ensureDomainProperties(domain, domainKind, domainKind.getRequiredProperties(), domainKind.getPropertyForeignKeys(domainContainer));
                    domain.save(user);
                }
                catch (ChangePropertyDescriptorException | BatchValidationException e)
                {
                    throw new UnexpectedException(e);
                }
            }
        }
        return def;
    }

    /**
     * Search folder, project, and shared for an existing domain
     *
     * @return null if no domain was located
     */
    @Nullable
    private Domain findExistingDomain(Container c, User user)
    {
        Domain domain;
        String uri = generateDomainURI(c, user);
        domain = PropertyService.get().getDomain(c, uri);

        if (domain == null)
        {
            uri = generateDomainURI(c.getProject(), user);
            domain = PropertyService.get().getDomain(c.getProject(), uri);
            if (domain == null)
            {
                uri = generateDomainURI(ContainerManager.getSharedContainer(), user);
                domain = PropertyService.get().getDomain(ContainerManager.getSharedContainer(), uri);
            }
        }
        return domain;
    }

    private void ensureDomainProperties(Domain domain, IssueDefDomainKind domainKind, Collection<PropertyStorageSpec> requiredProps, Set<PropertyStorageSpec.ForeignKey> foreignKeys)
    {
        String typeUri = domain.getTypeURI();
        Map<String, PropertyStorageSpec.ForeignKey> foreignKeyMap = new CaseInsensitiveHashMap<>();

        for (PropertyStorageSpec.ForeignKey fk : foreignKeys)
        {
            foreignKeyMap.put(fk.getColumnName(), fk);
        }

        for (PropertyStorageSpec spec : requiredProps)
        {
            DomainProperty prop = domain.addProperty();

            prop.setName(spec.getName());
            prop.setPropertyURI(typeUri + "#" + spec.getName());
            prop.setRangeURI(PropertyType.getFromJdbcType(spec.getJdbcType()).getTypeUri());
            prop.setScale(spec.getSize());
            prop.setRequired(!spec.isNullable());

            if (foreignKeyMap.containsKey(spec.getName()))
            {
                PropertyStorageSpec.ForeignKey fk = foreignKeyMap.get(spec.getName());
                Lookup lookup = new Lookup(domain.getContainer(), fk.getSchemaName(), domainKind.getLookupTableName(getName(), fk.getTableName()));

                prop.setLookup(lookup);
            }
        }
    }
}