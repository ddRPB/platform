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

package org.labkey.study.controllers.samples;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.*;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.study.Site;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.study.CohortFilter;
import org.labkey.study.SampleManager;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.*;
import org.labkey.study.query.SpecimenQueryView;
import org.labkey.study.samples.notifications.ActorNotificationRecipientSet;
import org.labkey.study.samples.notifications.DefaultRequestNotification;
import org.labkey.study.samples.notifications.NotificationRecipientSet;
import org.labkey.study.samples.notifications.RequestNotification;
import org.labkey.study.samples.settings.RepositorySettings;
import org.labkey.study.samples.settings.RequestNotificationSettings;
import org.labkey.study.security.permissions.ManageRequestsPermission;
import org.labkey.study.security.permissions.RequestSpecimensPermission;
import org.labkey.study.security.permissions.SetSpecimenCommentsPermission;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
 * Date: Dec 20, 2007
 * Time: 11:19:46 AM
 */
public class SpecimenUtils
{
    private BaseStudyController _controller;


    public SpecimenUtils(BaseStudyController controller)
    {
        // private constructor to prevent external instantiation
        _controller = controller;
    }

    public ViewContext getViewContext()
    {
        return _controller.getViewContext();
    }

    private Container getContainer()
    {
        return _controller.getViewContext().getContainer();
    }

    private User getUser()
    {
        return _controller.getViewContext().getUser();
    }

    private Study getStudy() throws ServletException
    {
        return _controller.getStudy();
    }

    public SpecimenQueryView getSpecimenQueryView(boolean showVials, boolean forExport, SpecimenQueryView.Mode viewMode, CohortFilter cohortFilter) throws ServletException, SQLException
    {
        return getSpecimenQueryView(showVials, forExport, null, viewMode, cohortFilter);
    }

    private String urlFor(Class<? extends Controller> action)
    {
        return urlFor(action, null);
    }

    private String urlFor(Class<? extends Controller> action, Map<Enum, String> parameters)
    {
        ActionURL url = new ActionURL(action, getContainer());
        if (parameters != null)
        {
            for (Map.Entry<Enum, String> entry : parameters.entrySet())
                url.addParameter(entry.getKey(), entry.getValue());
        }
        return url.getLocalURIString();
    }

    public static boolean isCommentsMode(Container container, SpecimenQueryView.Mode selectedMode) throws SQLException
    {
        return (selectedMode == SpecimenQueryView.Mode.COMMENTS) ||
                (selectedMode == SpecimenQueryView.Mode.DEFAULT && SampleManager.getInstance().getDisplaySettings(container).isDefaultToCommentsMode());
    }

    public SpecimenQueryView getSpecimenQueryView(boolean showVials, boolean forExport, ParticipantDataset[] cachedFilterData, SpecimenQueryView.Mode viewMode, CohortFilter cohortFilter) throws ServletException, SQLException
    {
        boolean commentsMode = isCommentsMode(getContainer(), viewMode);

        SpecimenQueryView gridView;
        RepositorySettings settings = SampleManager.getInstance().getRepositorySettings(getContainer());

        if (cachedFilterData != null)
            gridView = SpecimenQueryView.createView(getViewContext(), cachedFilterData,
                    showVials ? SpecimenQueryView.ViewType.VIALS :
                                SpecimenQueryView.ViewType.SUMMARY);
        else
            gridView = SpecimenQueryView.createView(getViewContext(),
                            showVials ? SpecimenQueryView.ViewType.VIALS :
                                        SpecimenQueryView.ViewType.SUMMARY, cohortFilter);
        gridView.setShowHistoryLinks(showVials && !forExport && !settings.isSimple());
        gridView.setDisableLowVialIndicators(forExport || settings.isSimple());
        gridView.setShowRecordSelectors(settings.isEnableRequests() || commentsMode);
        // if we're exporting, we can skip setting up the buttons:
        if (forExport)
            return gridView;

        List<DisplayElement> buttons = new ArrayList<DisplayElement>();

        ActionButton cohortButton = CohortManager.getInstance().createCohortButton(getViewContext(), cohortFilter);
        if (cohortButton != null)
            buttons.add(cohortButton);

        if (settings.isEnableRequests())
        {
            MenuButton requestMenuButton = new MenuButton("Request Options");
            requestMenuButton.addMenuItem("View Existing Requests", urlFor(SpecimenController.ViewRequestsAction.class));
            if (!commentsMode)
            {
                if (getViewContext().getContainer().hasPermission(getViewContext().getUser(), RequestSpecimensPermission.class))
                {
                    String dataRegionName = gridView.getSettings().getDataRegionName();
                    String createRequestURL = urlFor(SpecimenController.ShowCreateSampleRequestAction.class,
                            Collections.<Enum, String>singletonMap(SpecimenController.CreateSampleRequestForm.PARAMS.returnUrl,
                                    getViewContext().getActionURL().getLocalURIString()));

                    requestMenuButton.addMenuItem("Create New Request", "#",
                            "if (verifySelected(document.forms['" + dataRegionName + "'], '" + createRequestURL +
                            "', 'post', 'rows')) document.forms['" + dataRegionName + "'].submit();");

                    if (getViewContext().getContainer().hasPermission(getViewContext().getUser(), ManageRequestsPermission.class) ||
                            SampleManager.getInstance().isSpecimenShoppingCartEnabled(getViewContext().getContainer()))
                    {
                        requestMenuButton.addMenuItem("Add To Existing Request", "#",
                                "if (verifySelected(document.forms['" + dataRegionName + "'], '#', " +
                                "'get', 'rows')) showRequestWindow(LABKEY.DataRegions['" +
                                dataRegionName + "'].getChecked(), '" + (showVials ? SpecimenApiController.VialRequestForm.IdTypes.RowId
                                : SpecimenApiController.VialRequestForm.IdTypes.SpecimenHash) + "');");
                    }
                }
            }
            else
            {
                ActionURL endCommentsURL = getViewContext().getActionURL().clone();
                endCommentsURL.replaceParameter(SpecimenController.SampleViewTypeForm.PARAMS.viewMode, SpecimenQueryView.Mode.REQUESTS.name());
                requestMenuButton.addMenuItem("Enable Request Mode", endCommentsURL);
            }

            buttons.add(requestMenuButton);
        }

        if (getViewContext().getContainer().hasPermission(getUser(), SetSpecimenCommentsPermission.class))
        {
            boolean manualQCEnabled = SampleManager.getInstance().getDisplaySettings(getViewContext().getContainer()).isEnableManualQCFlagging();
            if (commentsMode)
            {
                MenuButton commentsMenuButton = new MenuButton("Comments" + (manualQCEnabled ? " and QC" : ""));
                String dataRegionName = gridView.getSettings().getDataRegionName();
                String setCommentsURL = urlFor(SpecimenController.UpdateCommentsAction.class);
                NavTree setItem = commentsMenuButton.addMenuItem("Set Vial Comment " + (manualQCEnabled ? "or QC State " : "") + "for Selected", "#",
                        "if (verifySelected(document.forms['" + dataRegionName + "'], '" + setCommentsURL +
                        "', 'post', 'rows')) document.forms['" + dataRegionName + "'].submit(); return false;");
                setItem.setId("Comments:Set");

                String clearCommentsURL = urlFor(SpecimenController.ClearCommentsAction.class);
                NavTree clearItem = commentsMenuButton.addMenuItem("Clear Vial Comments for Selected", "#",
                        "if (verifySelected(document.forms['" + dataRegionName + "'], '" + clearCommentsURL +
                        "', 'post', 'rows') && confirm('This will permanently clear comments for all selected vials.  " +
                                (manualQCEnabled ? "Quality control states will remain unchanged.  " : "" )+ "Continue?')) " +
                                "document.forms['" + dataRegionName + "'].submit();\nreturn false;");
                clearItem.setId("Comments:Clear");

                ActionURL endCommentsURL = getViewContext().getActionURL().clone();
                endCommentsURL.replaceParameter(SpecimenController.SampleViewTypeForm.PARAMS.viewMode, SpecimenQueryView.Mode.REQUESTS.name());
                NavTree exitItem = commentsMenuButton.addMenuItem("Exit Comments " + (manualQCEnabled ? "and QC " : "") + "mode", endCommentsURL);
                exitItem.setId("Comments:Exit");

                StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
                String subjectNoun = StudyService.get().getSubjectNounSingular(getContainer());
                boolean addSep = true;
                if (study.getParticipantCommentDataSetId() != null && study.getParticipantCommentDataSetId() != -1)
                {
                    DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, study.getParticipantCommentDataSetId());
                    if (def != null && def.canWrite(getUser()))
                    {
                        if (addSep)
                        {
                            commentsMenuButton.addSeparator();
                            addSep = false;
                        }
                        NavTree ptidComments = commentsMenuButton.addMenuItem("Manage " + subjectNoun + " Comments", new ActionURL(StudyController.DatasetAction.class, getContainer()).
                                addParameter("datasetId", study.getParticipantCommentDataSetId()));
                        ptidComments.setId("Comments:SetParticipant");
                    }
                }

                if (study.getParticipantVisitCommentDataSetId() != null && study.getParticipantVisitCommentDataSetId() != -1)
                {
                    DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, study.getParticipantVisitCommentDataSetId());
                    if (def != null && def.canWrite(getUser()))
                    {
                        if (addSep)
                        {
                            commentsMenuButton.addSeparator();
                            addSep = false;
                        }
                        NavTree ptidComments = commentsMenuButton.addMenuItem("Manage " + subjectNoun + "/Visit Comments", new ActionURL(StudyController.DatasetAction.class, getContainer()).
                                addParameter("datasetId", study.getParticipantVisitCommentDataSetId()));
                        ptidComments.setId("Comments:SetParticipantVisit");
                    }
                }
                buttons.add(commentsMenuButton);
            }
            else
            {
                ActionURL enableCommentsURL = getViewContext().getActionURL().clone();
                enableCommentsURL.replaceParameter(SpecimenController.SampleViewTypeForm.PARAMS.viewMode, SpecimenQueryView.Mode.COMMENTS.name());
                ActionButton commentsButton = new ActionButton("Enable Comments" + (manualQCEnabled ? "/QC" : ""), enableCommentsURL);
                buttons.add(commentsButton);
            }
        }


        if (getViewContext().hasPermission(ACL.PERM_ADMIN))
        {
            ActionButton upload = new ActionButton("button", "Import Specimens");
            upload.setURL(new ActionURL("Study-Samples", "showUploadSpecimens", getContainer()));
            buttons.add(upload);
        }

        if (settings.isEnableRequests() && getViewContext().getContainer().hasPermission(getViewContext().getUser(), RequestSpecimensPermission.class))
        {
            buttons.add(new ButtonBarLineBreak());
            buttons.add(new ExcludeSiteDropDown());
        }

        gridView.setButtons(buttons);
        return gridView;
    }



    public List<ActorNotificationRecipientSet> getPossibleNotifications(SampleRequest sampleRequest) throws SQLException
    {
        List<ActorNotificationRecipientSet> possibleNotifications = new ArrayList<ActorNotificationRecipientSet>();
        // allow notification of all parties listed in the request requirements:
        for (SampleRequestRequirement requirement : sampleRequest.getRequirements())
            addIfNotPresent(requirement.getActor(), requirement.getSite(), possibleNotifications);

        // allow notification of all site-based actors at the destination site, and all study-wide actors:
        Map<Integer, SiteImpl> relevantSites = new HashMap<Integer, SiteImpl>();
        if (sampleRequest.getDestinationSiteId() == null)
        {
            throw new IllegalStateException("Request " + sampleRequest.getRowId() + " in folder " +
                    sampleRequest.getContainer().getPath() + " does not have a valid destination site id.");
        }
        SiteImpl destSite = StudyManager.getInstance().getSite(sampleRequest.getContainer(), sampleRequest.getDestinationSiteId().intValue());
        relevantSites.put(destSite.getRowId(), destSite);
        for (Specimen specimen : sampleRequest.getSpecimens())
        {
            SiteImpl site = SampleManager.getInstance().getCurrentSite(specimen);
            if (site != null && !relevantSites.containsKey(site.getRowId()))
                relevantSites.put(site.getRowId(), site);
        }

        SampleRequestActor[] allActors = SampleManager.getInstance().getRequirementsProvider().getActors(sampleRequest.getContainer());
        // add study-wide actors and actors from all relevant sites:
        for (SampleRequestActor actor : allActors)
        {
            if (actor.isPerSite())
            {
                for (SiteImpl site : relevantSites.values())
                {
                    if (actor.isPerSite())
                        addIfNotPresent(actor, site, possibleNotifications);
                }
            }
            else
                addIfNotPresent(actor, null, possibleNotifications);
        }

        Collections.sort(possibleNotifications, new Comparator<ActorNotificationRecipientSet>()
        {
            public int compare(ActorNotificationRecipientSet first, ActorNotificationRecipientSet second)
            {
                String firstSite = first.getSite() != null ? first.getSite().getLabel() : "";
                String secondSite = second.getSite() != null ? second.getSite().getLabel() : "";
                int comp = firstSite.compareToIgnoreCase(secondSite);
                if (comp == 0)
                {
                    String firstActorLabel = first.getActor().getLabel();
                    if (firstActorLabel == null)
                        firstActorLabel = "";
                    String secondActorLabel = second.getActor().getLabel();
                    if (secondActorLabel == null)
                        secondActorLabel = "";
                    comp = firstActorLabel.compareToIgnoreCase(secondActorLabel);
                }
                return comp;
            }
        });
        return possibleNotifications;
    }

    private boolean addIfNotPresent(SampleRequestActor actor, SiteImpl site, List<ActorNotificationRecipientSet> list)
    {
        for (ActorNotificationRecipientSet actorSite : list)
        {
            if (actorSite.getActor().getRowId() == actor.getRowId())
            {
                if (actorSite.getSite() == null && site == null)
                    return false;
                else
                if (actorSite.getSite() != null && site != null && actorSite.getSite().getRowId() == site.getRowId())
                    return false;
            }
        }
        list.add(new ActorNotificationRecipientSet(actor, site));
        return true;
    }

    public static final class ExcludeSiteDropDown extends DisplayElement
    {
        public void render(RenderContext ctx, Writer out) throws IOException
        {
            ActionURL url = ctx.getViewContext().cloneActionURL();
            url.deleteParameter(SpecimenQueryView.PARAMS.excludeRequestedBySite.name());
            out.write("Hide Previously Requested By ");
            out.write("<select ");
            out.write("onchange=\"window.location=");
            out.write(PageFlowUtil.jsString(url.toString()+"&amp;excludeRequestedBySite="));
            out.write(" + this.options[this.selectedIndex].value;\"");
            out.write(">");
            out.write("<option value=''>&lt;Show All&gt;</option>");
            String excludeStr = ctx.getRequest().getParameter(SpecimenQueryView.PARAMS.excludeRequestedBySite.name());
            int siteId = null == StringUtils.trimToNull(excludeStr) ? 0 : Integer.parseInt(excludeStr);
            SiteImpl[] sites = StudyManager.getInstance().getSites(ctx.getContainer());
            for (SiteImpl site : sites)
            {
                out.write("<option value=\"");
                out.write(String.valueOf(site.getRowId()));
                out.write("\"");
                if (site.getRowId() == siteId)
                    out.write(" SELECTED ");
                out.write("\">");
                out.write(PageFlowUtil.filter(site.getDisplayName()));
                out.write("</option>");
            }
            out.write("</select>");
        }
    }

    public void writeExportData(SpecimenQueryView view, String type) throws Exception
    {
        if ("excel".equals(type))
            view.exportToExcel(_controller.getViewContext().getResponse());
        else if ("tsv".equals(type))
            view.exportToTsv(_controller.getViewContext().getResponse());
        else
            throw new IllegalArgumentException(type + " is not a supported export type.");
    }

    public void sendNewRequestNotifications(SampleRequest request) throws Exception
    {
        RequestNotificationSettings settings =
                SampleManager.getInstance().getRequestNotificationSettings(request.getContainer());
        Address[] notify = settings.getNewRequestNotifyAddresses();
        if (notify != null && notify.length > 0)
            sendNotification(new DefaultRequestNotification(request, Collections.singletonList(new NotificationRecipientSet(notify)), "New Request Created"));
    }

    public List<? extends NotificationRecipientSet> getNotifications(SampleRequest sampleRequest, String[] notificationIdPairs) throws SQLException
    {
        List<ActorNotificationRecipientSet> siteActors = new ArrayList<ActorNotificationRecipientSet>();
        if (notificationIdPairs == null || notificationIdPairs.length == 0)
            return siteActors;
        for (String notificationIdPair : notificationIdPairs)
            siteActors.add(ActorNotificationRecipientSet.getFromFormValue(sampleRequest.getContainer(), notificationIdPair));
        return siteActors;
    }

    public void sendNotification(RequestNotification notification) throws Exception
    {
        SampleRequest sampleRequest = notification.getSampleRequest();
        String specimenList = notification.getSpecimenListHTML(getViewContext());

        RequestNotificationSettings settings =
                SampleManager.getInstance().getRequestNotificationSettings(getContainer());
        MailHelper.ViewMessage message = MailHelper.createMessage(settings.getReplyToEmailAddress(getUser()), null);
        String subject = settings.getSubjectSuffix().replaceAll("%requestId%", "" + sampleRequest.getRowId());
        message.setSubject(getStudy().getLabel() + ": " + subject);
        JspView<NotificationBean> notifyView = new JspView<NotificationBean>("/org/labkey/study/view/samples/notification.jsp",
                new NotificationBean(getViewContext(), notification, specimenList));
        message.setTemplateContent(getViewContext().getRequest(), notifyView, "text/html");

        boolean first = true;
        for (NotificationRecipientSet recipient : notification.getRecipients())
        {
            for (String email : recipient.getEmailAddresses())
            {
                if (first)
                {
                    Address[] ccAddresses = settings.getCCAddresses();
                    if (ccAddresses != null && ccAddresses.length > 0)
                        message.setRecipients(Message.RecipientType.CC, ccAddresses);
                    first = false;
                }
                else
                    message.setRecipients(Message.RecipientType.CC, "");

                message.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
                MailHelper.send(message);
                MailHelper.addAuditEvent(getUser(), getContainer(), message);
            }
            if (notification.getRequirement() != null)
                SampleManager.getInstance().createRequestEvent(getUser(), notification.getRequirement(),
                        SampleManager.RequestEventType.NOTIFICATION_SENT, "Notification sent to " + recipient.getLongRecipientDescription(), null);
            else
                SampleManager.getInstance().createRequestEvent(getUser(), sampleRequest,
                        SampleManager.RequestEventType.NOTIFICATION_SENT, "Notification sent to " + recipient.getLongRecipientDescription(), null);
        }
    }

    public static class NotificationBean
    {
        private User _user;
        private String _baseServerURI;
        private String _specimenList;
        private RequestNotification _notification;

        public NotificationBean(ViewContext context, RequestNotification notification, String specimenList)
        {
            _notification = notification;
            _user = context.getUser();
            _baseServerURI = context.getActionURL().getBaseServerURI();
            _specimenList = specimenList;
        }

        public Attachment[] getAttachments()
        {
            return _notification.getAttachments();
        }

        public String getComments()
        {
            return _notification.getComments();
        }

        public int getRequestId()
        {
            return _notification.getSampleRequest().getRowId();
        }

        public String getModifyingUser()
        {
            return _user.getEmail();
        }

        public String getRequestingSiteName() throws SQLException
        {
            Site destSite = StudyManager.getInstance().getSite(_notification.getSampleRequest().getContainer(),
                    _notification.getSampleRequest().getDestinationSiteId());
            if (destSite != null)
                return destSite.getDisplayName();
            else
                return null;
        }

        public String getStatus() throws SQLException
        {
            SampleRequestStatus status = SampleManager.getInstance().getRequestStatus(_notification.getSampleRequest().getContainer(),
                    _notification.getSampleRequest().getStatusId());
            return status != null ? status.getLabel() : "Unknown";
        }

        public Container getContainer()
        {
            return _notification.getSampleRequest().getContainer();
        }

        public String getEventDescription()
        {
            return _notification.getEventSummary();
        }

        public String getBaseServerURI()
        {
            return _baseServerURI;
        }

        public String getRequestDescription()
        {
            return _notification.getSampleRequest().getComments();
        }

        public String getSpecimenList()
        {
            return _specimenList;
        }
    }

    public static <T> Set<T> intersect(Set<T> left, Set<T> right)
    {
        Set<T> intersection = new HashSet<T>();
        for (T item : left)
        {
            if (right.contains(item))
                intersection.add(item);
        }
        return intersection;
    }

    public static Collection<Integer> getPreferredProvidingLocations(Collection<List<Specimen>> specimensBySample) throws SQLException
    {
        Set<Integer> locationIntersection = null;
        for (List<Specimen> vials : specimensBySample)
        {
            Set<Integer> currentLocations = new HashSet<Integer>();
            for (Specimen vial : vials)
            {
                if (vial.getCurrentLocation() != null)
                    currentLocations.add(vial.getCurrentLocation());
            }
            if (locationIntersection == null)
                locationIntersection = currentLocations;
            else
            {
                locationIntersection = intersect(locationIntersection, currentLocations);
                if (locationIntersection.isEmpty())
                    return locationIntersection;
            }
        }
        return locationIntersection;
    }

    public void ensureSpecimenRequestsConfigured() throws ServletException, SQLException
    {
        SampleRequestStatus[] statuses = SampleManager.getInstance().getRequestStatuses(getContainer(), getUser());
        if (statuses == null || statuses.length == 1)
            HttpView.throwRedirect(new ActionURL(SpecimenController.SpecimenRequestConfigRequired.class, getContainer()));
    }


    public Specimen[] getSpecimensFromRowIds(int[] requestedSampleIds) throws SQLException
    {
        Specimen[] requestedSpecimens = null;
        if (requestedSampleIds != null)
        {
            List<Specimen> specimens = new ArrayList<Specimen>();
            for (int requestedSampleId : requestedSampleIds)
            {
                Specimen current = SampleManager.getInstance().getSpecimen(getContainer(), requestedSampleId);
                if (current != null)
                    specimens.add(current);
            }
            requestedSpecimens = specimens.toArray(new Specimen[specimens.size()]);
        }
        return requestedSpecimens;

    }

    public Specimen[] getSpecimensFromGlobalUniqueIds(Set<String> globalUniqueIds) throws SQLException
    {
        Specimen[] requestedSpecimens = null;
        if (globalUniqueIds != null)
        {
            List<Specimen> specimens = new ArrayList<Specimen>();
            for (String globalUniqueId : globalUniqueIds)
            {
                Specimen match = SampleManager.getInstance().getSpecimen(getContainer(), globalUniqueId);
                if (match != null)
                    specimens.add(match);
            }
            requestedSpecimens = specimens.toArray(new Specimen[specimens.size()]);
        }
        return requestedSpecimens;

    }

    public Specimen[] getSpecimensFromRowIds(Collection<String> ids) throws SQLException
    {
        return getSpecimensFromRowIds(BaseStudyController.toIntArray(ids));
    }

    public Specimen[] getSpecimensFromPost(boolean fromGroupedView, boolean onlyAvailable) throws SQLException
    {
        Set<String> formValues = null;
        if ("POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()))
            formValues = DataRegionSelection.getSelected(getViewContext(), true);

        if (formValues == null || formValues.isEmpty())
            return null;

        Specimen[] selectedVials;
        if (fromGroupedView)
        {
            Map<String, List<Specimen>> keyToVialMap =
                    SampleManager.getInstance().getVialsForSampleHashes(getContainer(), formValues, onlyAvailable);
            List<Specimen> vials = new ArrayList<Specimen>();
            for (List<Specimen> vialList : keyToVialMap.values())
                vials.addAll(vialList);
            selectedVials = vials.toArray(new Specimen[vials.size()]);
        }
        else
            selectedVials = getSpecimensFromRowIds(formValues);
        return selectedVials;
    }

    public static class AmbiguousLocationException extends Exception
    {
        private Container _container;
        private Collection<Integer> _possibleLocationIds;
        private SiteImpl[] _possibleLocations = null;

        public AmbiguousLocationException(Container container, Collection<Integer> possibleLocationIds)
        {
            _container = container;
            _possibleLocationIds = possibleLocationIds;
        }

        public Collection<Integer> getPossibleLocationIds()
        {
            return _possibleLocationIds;
        }

        public SiteImpl[] getPossibleLocations()
        {
            if (_possibleLocations == null)
            {
                _possibleLocations = new SiteImpl[_possibleLocationIds.size()];
                int idx = 0;
                try
                {
                    for (Integer id : _possibleLocationIds)
                        _possibleLocations[idx++] = StudyManager.getInstance().getSite(_container, id.intValue());
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
            }
            return _possibleLocations;
        }
    }

    public static class RequestedSpecimens
    {
        private Collection<Integer> _providingLocationIds;
        private Specimen[] _specimens;
        private Site[] _providingLocations;

        public RequestedSpecimens(Specimen[] specimens, Collection<Integer> providingLocationIds)
        {
            _specimens = specimens;
            _providingLocationIds = providingLocationIds;
        }

        public RequestedSpecimens(Specimen[] specimens)
        {
            _specimens = specimens;
            _providingLocationIds = new HashSet<Integer>();
            if (specimens != null)
            {
                for (Specimen vial : specimens)
                    _providingLocationIds.add(vial.getCurrentLocation());
            }
        }

        public Site[] getProvidingLocations()
        {
            if (_providingLocations == null)
            {
                if (_specimens == null || _specimens.length == 0)
                    _providingLocations = new Site[0];
                else
                {
                    Container container = _specimens[0].getContainer();
                    _providingLocations = new Site[_providingLocationIds.size()];
                    int siteIndex = 0;
                    try
                    {
                        for (Integer siteId : _providingLocationIds)
                            _providingLocations[siteIndex++] = StudyManager.getInstance().getSite(container, siteId.intValue());
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeSQLException(e);
                    }
                }
            }
            return _providingLocations;
        }

        public Specimen[] getSpecimens()
        {
            return _specimens;
        }
    }

    public RequestedSpecimens getRequestableByVialRowIds(Set<String> rowIds) throws SQLException
    {
        Specimen[] requestedSamples = getSpecimensFromRowIds(rowIds);
        return new RequestedSpecimens(requestedSamples);
    }

    public RequestedSpecimens getRequestableByVialGlobalUniqueIds(Set<String> globalUniqueIds) throws SQLException
    {
        Specimen[] requestedSamples = getSpecimensFromGlobalUniqueIds(globalUniqueIds);
        return new RequestedSpecimens(requestedSamples);
    }

    public RequestedSpecimens getRequestableBySampleHash(Set<String> formValues, Integer preferredLocation) throws SQLException, AmbiguousLocationException
    {
        Map<String, List<Specimen>> vialsByHash = SampleManager.getInstance().getVialsForSampleHashes(getContainer(), formValues, true);

        if (vialsByHash == null || vialsByHash.isEmpty())
            return new RequestedSpecimens(new Specimen[0]);

        if (preferredLocation == null)
        {
            Collection<Integer> preferredLocations = getPreferredProvidingLocations(vialsByHash.values());
            if (preferredLocations.size() == 1)
                preferredLocation = preferredLocations.iterator().next();
            else if (preferredLocations.size() > 1)
                throw new AmbiguousLocationException(getContainer(), preferredLocations);
        }
        Specimen[] requestedSamples = new Specimen[vialsByHash.size()];

        int i = 0;
        Set<Integer> providingLocations = new HashSet<Integer>();
        for (List<Specimen> vials : vialsByHash.values())
        {
            Specimen selectedVial = null;
            if (preferredLocation == null)
                selectedVial = vials.get(0);
            else
            {
                for (Iterator<Specimen> it = vials.iterator(); it.hasNext() && selectedVial == null;)
                {
                    Specimen vial = it.next();
                    if (vial.getCurrentLocation() != null && vial.getCurrentLocation().intValue() == preferredLocation.intValue())
                        selectedVial = vial;
                }
            }
            if (selectedVial == null)
                throw new IllegalStateException("Vial was not available from specified location " + preferredLocation);
            providingLocations.add(selectedVial.getCurrentLocation());
            requestedSamples[i++] = selectedVial;
        }
        return new RequestedSpecimens(requestedSamples, providingLocations);
    }
    
    public GridView getRequestEventGridView(HttpServletRequest request, BindException errors, SimpleFilter filter)
    {
        DataRegion rgn = new DataRegion();
        TableInfo tableInfoRequestEvent = StudySchema.getInstance().getTableInfoSampleRequestEvent();
        rgn.setTable(tableInfoRequestEvent);
        rgn.setColumns(tableInfoRequestEvent.getColumns("Created", "EntryType", "Comments", "CreatedBy", "EntityId"));
        rgn.getDisplayColumn("EntityId").setVisible(false);
        rgn.setShadeAlternatingRows(true);
        rgn.setShowBorders(true);
        DataColumn commentsColumn = (DataColumn) rgn.getDisplayColumn("Comments");
        commentsColumn.setWidth("50%");
        commentsColumn.setPreserveNewlines(true);
        rgn.addDisplayColumn(new AttachmentDisplayColumn(request));
        rgn.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
        GridView grid = new GridView(rgn, errors);
        grid.setFilter(filter);
        grid.setSort(new Sort("Created"));
        return grid;
    }

    private static class AttachmentDisplayColumn extends SimpleDisplayColumn
    {
        private HttpServletRequest _request;
        public AttachmentDisplayColumn(HttpServletRequest request)
        {
            super();
            _request = request;
            setCaption("Attachments");
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Map cols = ctx.getRow();
            SampleRequestEvent event = ObjectFactory.Registry.getFactory(SampleRequestEvent.class).fromMap(cols);
            Attachment[] attachments = AttachmentService.get().getAttachments(event);

            if (attachments != null && attachments.length > 0)
            {
                for (Attachment attachment : attachments)
                {
                    out.write("<a href=\"" + PageFlowUtil.filter(attachment.getDownloadUrl("Study-Samples")) + "\">");
                    out.write("<img src=\"" + _request.getContextPath() + attachment.getFileIcon() + "\">&nbsp;");
                    out.write(PageFlowUtil.filter(attachment.getName()));
                    out.write("</a><br>");
                }
            }
            else
                out.write("&nbsp;");
        }
    }

    public SimpleFilter getSpecimenListFilter(SampleRequest sampleRequest, SiteImpl srcSite,
                                              SpecimenController.LabSpecimenListsBean.Type type) throws SQLException
    {
        SpecimenController.LabSpecimenListsBean bean = new SpecimenController.LabSpecimenListsBean(this, sampleRequest, type);
        List<Specimen> specimens = bean.getSpecimens(srcSite);
        Object[] params = new Object[specimens.size() + 1];
        params[params.length - 1] = sampleRequest.getContainer().getId();
        StringBuilder whereClause = new StringBuilder();
        whereClause.append("(");
        int i = 0;
        for (Specimen specimen : specimens)
        {
            if (i > 0)
                whereClause.append(" OR ");
            whereClause.append("RowId = ?");
            params[i++] = specimen.getRowId();
        }
        whereClause.append(") AND Container = ?");

        SimpleFilter filter = new SimpleFilter();
        filter.addWhereClause(whereClause.toString(), params, "RowId", "Container");
        return filter;
    }

    private String getSpecimenListFileName(SiteImpl srcSite, SiteImpl destSite)
    {
        StringBuilder filename = new StringBuilder();
        filename.append(getShortSiteLabel(srcSite)).append("_to_").append(getShortSiteLabel(destSite));
        filename.append("_").append(DateUtil.formatDate());
        return filename.toString();
    }

    public TSVGridWriter getSpecimenListTsvWriter(SampleRequest sampleRequest, SiteImpl srcSite,
                                                   SiteImpl destSite, SpecimenController.LabSpecimenListsBean.Type type) throws SQLException, IOException
    {
        DataRegion dr = new DataRegion();
        dr.setTable(StudySchema.getInstance().getTableInfoSpecimenDetail());
        dr.setColumns(StudySchema.getInstance().getTableInfoSpecimenDetail().getColumns(
                "GlobalUniqueId, Ptid, VisitValue, Volume, VolumeUnits, " +
                        "DrawTimestamp, ProtocolNumber"));
        RenderContext ctx = new RenderContext(getViewContext());
        ctx.setContainer(sampleRequest.getContainer());
        ctx.setBaseFilter(getSpecimenListFilter(sampleRequest, srcSite, type));
        ResultSet rs = dr.getResultSet(ctx);
        List<DisplayColumn> cols = dr.getDisplayColumns();
        TSVGridWriter tsv = new TSVGridWriter(rs, cols);
        tsv.setFilenamePrefix(getSpecimenListFileName(srcSite, destSite));
        tsv.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.propertyName);
        return tsv;
    }

    public ExcelWriter getSpecimenListXlsWriter(SampleRequest sampleRequest, SiteImpl srcSite,
                                                 SiteImpl destSite, SpecimenController.LabSpecimenListsBean.Type type) throws SQLException, IOException
    {
        DataRegion dr = new DataRegion();
        dr.setTable(StudySchema.getInstance().getTableInfoSpecimenDetail());
        dr.setColumns(StudySchema.getInstance().getTableInfoSpecimenDetail().getColumns(
                "GlobalUniqueId, Ptid, VisitValue, Volume, VolumeUnits, " +
                        "DrawTimestamp, ProtocolNumber"));
        RenderContext ctx = new RenderContext(getViewContext());
        ctx.setContainer(sampleRequest.getContainer());
        ctx.setBaseFilter(getSpecimenListFilter(sampleRequest, srcSite, type));
        ResultSet rs = dr.getResultSet(ctx);
        List<DisplayColumn> cols = dr.getDisplayColumns();
        ExcelWriter xl = new ExcelWriter(rs, cols);
        xl.setFilenamePrefix(getSpecimenListFileName(srcSite, destSite));
        return xl;
    }

    private String getShortSiteLabel(SiteImpl site)
    {
        String label;
        if (site.getLabel() != null && site.getLabel().length() > 0)
            label = site.getLabel().substring(0, Math.min(site.getLabel().length(), 15));
        else if (site.getLdmsLabCode() != null)
            label = "ldmsId" + site.getLdmsLabCode();
        else if (site.getLabwareLabCode() != null && site.getLabwareLabCode().length() > 0)
            label = "labwareId" + site.getLabwareLabCode();
        else
            label = "rowId" + site.getRowId();
        return label.replaceAll("\\W+", "_");
    }


    public static boolean isFieldTrue(RenderContext ctx, String fieldName)
    {
        Object value = ctx.getRow().get(fieldName);
        if (value instanceof Integer)
            return ((Integer) value).intValue() != 0;
        else if (value instanceof Boolean)
            return ((Boolean) value).booleanValue();
        return false;
    }
}
