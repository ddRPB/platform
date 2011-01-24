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

package org.labkey.bigiron.sas;

import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectFactory;
import org.labkey.api.util.VersionNumber;

import java.util.Collection;
import java.util.Collections;

/*
* User: adam
* Date: Nov 26, 2010
* Time: 10:34:33 PM
*/
public class Sas92DialectFactory extends SqlDialectFactory
{
    private String getProductName()
    {
        return "SAS";
    }

    @Override
    public boolean claimsDriverClassName(String driverClassName)
    {
        return false;    // Only used to create a new database, which we never do on SAS
    }

    @Override
    // SAS/SHARE driver throws when invoking DatabaseMetaData database version methods, so use the jdbcDriverVersion to determine dialect version
    public boolean claimsProductNameAndVersion(String dataBaseProductName, VersionNumber databaseProductVersion, String jdbcDriverVersion, boolean logWarnings)
    {
        return dataBaseProductName.equals(getProductName()) && jdbcDriverVersion.startsWith("9.2");
    }

    @Override
    public SqlDialect create()
    {
        return new Sas92Dialect();
    }

    @Override
    public Collection<? extends Class> getJUnitTests()
    {
        return Collections.emptyList();
    }
}
