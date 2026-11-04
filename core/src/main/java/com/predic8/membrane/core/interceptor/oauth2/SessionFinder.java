/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2;

import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager.Session;

import javax.annotation.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SessionFinder {

    /**
     * The client and redirect URI of the authorization request a code was issued for. Kept per code, not in the
     * session, as one browser session can authorize several clients.
     */
    public record CodeIssuance(String clientId, String redirectUri) {}

    private final ConcurrentHashMap<String, SessionManager.Session> authCodesToSession = new ConcurrentHashMap<>();

    @GuardedBy("authCodesToSession")
    private final Map<String, CodeIssuance> authCodesToIssuance = new HashMap<>();

    private final ConcurrentHashMap<String, SessionManager.Session> tokensToSession = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, SessionManager.Session> refreshTokensToSession = new ConcurrentHashMap<>();

    public void addSessionForCode(String code, SessionManager.Session session, CodeIssuance issuance) {
        synchronized (authCodesToSession) {
            authCodesToSession.put(code, session);
            authCodesToIssuance.put(code, issuance);
        }
    }

    public CodeIssuance getIssuanceForCode(String code) {
        synchronized (authCodesToSession) {
            return authCodesToIssuance.get(code);
        }
    }

    public void addSessionForToken(String token, SessionManager.Session session) {
        synchronized (tokensToSession) {
            tokensToSession.put(token, session);
        }
    }

    public void addSessionForRefreshToken(String refreshToken, SessionManager.Session session) {
        synchronized (refreshTokensToSession) {
            refreshTokensToSession.put(refreshToken, session);
        }
    }

    public boolean hasSessionForCode(String code) {
        synchronized (authCodesToSession) {
            return authCodesToSession.containsKey(code);
        }
    }

    public boolean hasSessionForToken(String token) {
        synchronized (tokensToSession) {
            return tokensToSession.containsKey(token);
        }
    }

    public SessionManager.Session getSessionForCode(String code) {
        synchronized (authCodesToSession) {
            Session session = authCodesToSession.get(code);
            if (session != null) {
                session.touch();
            }
            return session;
        }
    }

    public SessionManager.Session getSessionForToken(String token) {
        synchronized (tokensToSession) {
            Session session = tokensToSession.get(token);
            if (session != null) {
                session.touch();
            }
            return session;
        }
    }

    public SessionManager.Session getSessionForRefreshToken(String refreshToken) {
        synchronized (refreshTokensToSession) {
            Session session = refreshTokensToSession.get(refreshToken);
            if (session != null) {
                session.touch();
            }
            return session;
        }
    }

    public void removeSessionForCode(String code) {
        synchronized (authCodesToSession) {
            authCodesToSession.remove(code);
            authCodesToIssuance.remove(code);
        }
    }

    public void removeSessionForToken(String token) {
        synchronized (tokensToSession) {
            tokensToSession.remove(token);
        }
    }

    public void removeSessionForRefreshToken(String refreshToken) {
        synchronized (refreshTokensToSession) {
            refreshTokensToSession.remove(refreshToken);
        }
    }

    public void cleanupSessions(Set<SessionManager.Session> sessionsToRemove) {

        cleanupMap(sessionsToRemove, authCodesToSession);
        synchronized (authCodesToSession) {
            authCodesToIssuance.keySet().retainAll(authCodesToSession.keySet());
        }
        cleanupMap(sessionsToRemove, tokensToSession);
        cleanupMap(sessionsToRemove, refreshTokensToSession);

    }

    private void cleanupMap(Set<SessionManager.Session> sessionsToRemove, ConcurrentHashMap<String, Session> sessionMap) {

        synchronized (sessionMap) {

            List<String> keysToRemove = new ArrayList<String>();
            for (Map.Entry<String, SessionManager.Session> entry : sessionMap.entrySet()) {
                String key = entry.getKey();
                SessionManager.Session value = entry.getValue();

                if (sessionsToRemove.contains(value)) {
                    keysToRemove.add(key);
                }
            }

            for (String key : keysToRemove) {
                sessionMap.remove(key);
            }
        }
    }
}
