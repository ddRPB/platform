/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.api.assay.dilution;

import org.labkey.api.data.statistics.CurveFit;
import org.labkey.api.data.statistics.DoublePoint;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.study.WellData;
import org.labkey.api.study.WellGroup;

/**
 * User: brittp
 * Date: Oct 25, 2006
 * Time: 4:58:12 PM
 */
public interface DilutionCurve
{
    public class FitFailedException extends Exception
    {
        public FitFailedException(String message)
        {
            super(message);
        }
    }

    DoublePoint[] getCurve() throws FitFailedException;

    CurveFit.Parameters getParameters() throws FitFailedException;

    double getFitError() throws FitFailedException;

    double getCutoffDilution(double percent) throws FitFailedException;

    double getInterpolatedCutoffDilution(double percent);

    double getMinDilution() throws FitFailedException;

    double getMaxDilution() throws FitFailedException;

    double fitCurve(double x, CurveFit.Parameters curveParameters);

    double calculateAUC(StatsService.AUCType type) throws FitFailedException;

    public static interface PercentCalculator
    {
        double getPercent(WellGroup group, WellData data) throws FitFailedException;
    }
}
