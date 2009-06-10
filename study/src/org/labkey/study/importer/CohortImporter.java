/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.importer;

import org.apache.xmlbeans.XmlException;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.CohortManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.CohortType;
import org.labkey.study.xml.CohortsDocument;
import org.labkey.study.xml.StudyDocument;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 9:26:29 PM
 */
public class CohortImporter
{
    void process(StudyImpl study, ImportContext ctx, File root) throws IOException, SQLException, ServletException, StudyImporter.StudyImportException
    {
        StudyDocument.Study.Cohorts cohortsXml = ctx.getStudyXml().getCohorts();

        if (null != cohortsXml)
        {
            CohortType.Enum cohortType = cohortsXml.getType();

            if (cohortType == CohortType.AUTOMATIC)
            {
                Integer dataSetId = cohortsXml.getDatasetId();
                String dataSetProperty = cohortsXml.getDatasetProperty();
                CohortManager.updateAutomaticCohortAssignment(study, ctx.getUser(), dataSetId, dataSetProperty);
            }
            else
            {
                File cohortFile = StudyImporter.getStudyFile(root, root, cohortsXml.getFile(), "Study.xml");
                CohortsDocument cohortAssignmentXml;

                try
                {
                    cohortAssignmentXml = CohortsDocument.Factory.parse(cohortFile);
                }
                catch (XmlException e)
                {
                    throw new StudyImporter.InvalidFileException(root, cohortFile, e);
                }

                Map<String, Integer> p2c = new HashMap<String, Integer>();
                CohortsDocument.Cohorts.Cohort[] cohortXmls = cohortAssignmentXml.getCohorts().getCohortArray();

                for (CohortsDocument.Cohorts.Cohort cohortXml : cohortXmls)
                {
                    String label = cohortXml.getLabel();
                    CohortImpl cohort = CohortManager.createCohort(study, ctx.getUser(), label);

                    for (String ptid : cohortXml.getIdArray())
                        p2c.put(ptid, cohort.getRowId());
                }

                CohortManager.updateManualCohortAssignment(study, ctx.getUser(), p2c);
            }
        }
    }
}
