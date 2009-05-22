/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

CREATE VIEW study.LockedSpecimens AS
  SELECT map.RowId, map.SpecimenGlobalUniqueId AS GlobalUniqueId, map.Container FROM study.SampleRequest AS request
      JOIN study.SampleRequestStatus AS status ON request.StatusId = status.RowId AND status.SpecimensLocked = True
      JOIN study.SampleRequestSpecimen AS map ON request.rowid = map.SampleRequestId AND map.Orphaned = False;

CREATE VIEW study.SpecimenDetail AS
    SELECT SpecimenInfo.*,
    -- eliminate nulls in my left-outer-join fields:
    (
        CASE Requestable
        WHEN True THEN (
            CASE LockedInRequest
            WHEN True THEN False
            ELSE True
            END)
        WHEN False THEN False
        ELSE (
	    CASE AtRepository
            WHEN True THEN (
                CASE LockedInRequest
                WHEN True THEN False
                ELSE True
                END)
            ELSE False
            END)
	END
        ) AS Available
         FROM
            (
               SELECT Specimen.*,
                    (CASE Repository WHEN True THEN True ELSE False END) AS AtRepository,
                     Site.Label AS SiteName,Site.LdmsLabCode AS SiteLdmsCode,
                     (CASE LockedSpecimens.Locked WHEN True THEN True ELSE False END) AS LockedInRequest
    	        FROM study.Specimen AS Specimen
                LEFT OUTER JOIN study.Site AS Site ON
                        (Site.RowId = Specimen.CurrentLocation)
                LEFT OUTER JOIN (SELECT *, True AS Locked FROM study.LockedSpecimens) LockedSpecimens ON
                    LockedSpecimens.GlobalUniqueId = Specimen.GlobalUniqueId AND
                    LockedSpecimens.Container = Specimen.Container
        ) SpecimenInfo;

CREATE VIEW study.SpecimenSummary AS
        SELECT Container, SpecimenHash, Ptid, VisitDescription, VisitValue,
        SUM(Volume) AS TotalVolume, SUM(CASE Available WHEN True THEN Volume ELSE 0 END) AS AvailableVolume,
        VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId, DrawTimestamp, SalReceiptDate,
        ClassId, ProtocolNumber, SubAdditiveDerivative, OriginatingLocationId,
        PrimaryVolume, PrimaryVolumeUnits, DerivativeTypeId2,
        COUNT(GlobalUniqueId) AS VialCount,
        SUM(CASE LockedInRequest WHEN True THEN 1 ELSE 0 END) AS LockedInRequestCount,
        SUM(CASE AtRepository WHEN True THEN 1 ELSE 0 END) AS AtRepositoryCount,
        SUM(CASE Available WHEN True THEN 1 ELSE 0 END) AS AvailableCount
    FROM study.SpecimenDetail
    GROUP BY Container, SpecimenHash, Ptid, VisitDescription,
        VisitValue, VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId,
        PrimaryVolume, PrimaryVolumeUnits, DerivativeTypeId2,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, SubAdditiveDerivative,
        OriginatingLocationId;
