/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.exp.api;

import org.labkey.api.security.User;
import org.labkey.api.exp.PropertyDescriptor;

import java.util.Date;
import java.util.List;

public interface ExpProtocolApplication extends ExpObject
{
    public ExpDataRunInput[] getDataInputs();
    public List<ExpData> getInputDatas();
    public List<ExpData> getOutputDatas();
    public ExpMaterialRunInput[] getMaterialInputs();
    public List<ExpMaterial> getInputMaterials();

    List<ExpMaterial> getOutputMaterials();

    public ExpProtocol getProtocol();

    /**
     * Add a data input
     * @param user
     * @param input
     * @param inputRole optional argument specifying the input role name
     */
    public void addDataInput(User user, ExpData input, String inputRole);
    public void removeDataInput(User user, ExpData data);
    public void addMaterialInput(User user, ExpMaterial material, String inputRole);
    public void removeMaterialInput(User user, ExpMaterial material);

    public ExpRun getRun();
    public int getActionSequence();
    public ExpProtocol.ApplicationType getApplicationType();

    Date getActivityDate();

    String getComments();

    void save(User user);
}
