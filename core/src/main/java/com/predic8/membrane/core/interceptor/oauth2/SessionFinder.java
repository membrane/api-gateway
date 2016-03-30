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

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class SessionFinder {

    private ConcurrentHashMap<String, SessionManager.Session> authCodesToSession = new ConcurrentHashMap<String, SessionManager.Session>();
    private ConcurrentHashMap<String, SessionManager.Session> tokensToSession = new ConcurrentHashMap<String, SessionManager.Session>();

    public void addSessionForCode(String code, SessionManager.Session session){
        synchronized (authCodesToSession) {
            authCodesToSession.put(code, session);
        }
    }

    public void addSessionForToken(String token, SessionManager.Session session){
        synchronized (tokensToSession){
            tokensToSession.put(token,session);
        }
    }

    public boolean hasSessionForCode(String code){
        synchronized (authCodesToSession){
            return authCodesToSession.containsKey(code);
        }
    }

    public boolean hasSessionForToken(String token){
        synchronized (tokensToSession){
            return tokensToSession.containsKey(token);
        }
    }

    public SessionManager.Session getSessionForCode(String code){
        synchronized(authCodesToSession){
            return authCodesToSession.get(code);
        }
    }

    public SessionManager.Session getSessionForToken(String token){
        synchronized(tokensToSession){
            return tokensToSession.get(token);
        }
    }

    public void removeSessionForCode(String code){
        synchronized(authCodesToSession){
            authCodesToSession.remove(code);
        }
    }

    public void removeSessionForToken(String token){
        synchronized(tokensToSession){
            tokensToSession.remove(token);
        }
    }
}
