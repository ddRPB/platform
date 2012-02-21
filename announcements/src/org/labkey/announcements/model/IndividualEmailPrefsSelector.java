/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.announcements.model;

import org.labkey.api.data.Container;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: Mar 4, 2007
 * Time: 10:00:34 PM
 */
public class IndividualEmailPrefsSelector extends EmailPrefsSelector
{
    public IndividualEmailPrefsSelector(Container c)
    {
        super(c);
    }


    @Override
    protected boolean includeEmailPref(MessageConfigService.UserPreference ep)
    {
        return super.includeEmailPref(ep) && ((ep.getEmailOptionId() & AnnouncementManager.EMAIL_NOTIFICATION_TYPE_DIGEST) == 0);
    }


    public Set<User> getNotificationUsers(AnnouncementModel ann)
    {
        Set<User> authorized = new HashSet<User>(_emailPrefs.size());

        String srcIdentifier = ann.lookupSrcIdentifer();

        List<MessageConfigService.UserPreference> relevantPrefs = new ArrayList<MessageConfigService.UserPreference>(_emailPrefs.size());
        Set<User> directlySubscribedUsers = new HashSet<User>();
        // First look through for users that have subscriptions for this srcIdentfier directly
        for (MessageConfigService.UserPreference ep : _emailPrefs)
        {
            if (ep.getSrcIdentifier().equals(srcIdentifier))
            {
                // Remember the permission, and the user
                relevantPrefs.add(ep);
                directlySubscribedUsers.add(ep.getUser());
            }
        }

        for (MessageConfigService.UserPreference ep : _emailPrefs)
        {
            // Then look for users that didn't have a direct subscription, but have one set at the container level
            if (ep.getSrcIdentifier().equals(ann.getContainerId()) && !ep.getSrcIdentifier().equals(srcIdentifier) && !directlySubscribedUsers.contains(ep.getUser()))
            {
                relevantPrefs.add(ep);
            }
        }

        for (MessageConfigService.UserPreference ep : relevantPrefs)
            if (shouldSend(ann, ep))
                authorized.add(ep.getUser());

        authorized.addAll(AnnouncementManager.getAnnouncement(ann.lookupContainer(), ann.getEntityId(), true).getMemberList());

        return authorized;
    }
}
