/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpSession;

/**
 * API keys are bound to a single HTTP session, and offer a way to authenticate headless clients when they are
 * targeting data that requires user interaction, like two-factor authentication or accepting terms of use.
 * Created by adam on 4/3/2016.
 */
public class SessionApiKeyManager extends SessionKeyManager<HttpSession>
{
    private final static SessionApiKeyManager INSTANCE = new SessionApiKeyManager();

    public static SessionApiKeyManager get()
    {
        return INSTANCE;
    }

    private SessionApiKeyManager()
    {
    }

    @Override
    @NotNull String getSessionAttributeName()
    {
        return "apikeys";
    }

    @Override
    @Nullable String getKeyPrefix()
    {
        return "session";
    }

    @Override
    HttpSession createContext(HttpSession session, User user)
    {
        return session;
    }

    @Override
    HttpSession validateContext(HttpSession session, String apiKey)
    {
        return isKeyInSession(session, apiKey) ? session : null;
    }
}
