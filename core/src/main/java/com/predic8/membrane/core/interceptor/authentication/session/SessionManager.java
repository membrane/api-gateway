/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.authentication.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.stream.XMLStreamReader;

import org.apache.commons.lang.StringUtils;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.authentication.session.CleanupThread.Cleaner;

public class SessionManager extends AbstractXmlElement implements Cleaner {
	private String cookieName;
	long timeout;
	
	// TODO: bind session also to remote IP (for public Membrane release)
	HashMap<String, Session> sessions = new HashMap<String, SessionManager.Session>();
	
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		cookieName = token.getAttributeValue("", "cookieName");
		timeout = Long.parseLong(StringUtils.defaultIfEmpty(token.getAttributeValue("", "timeout"), "300000"));
	}
	
	public void init(Router router) {
		cookieName = StringUtils.defaultIfEmpty(cookieName, "SESSIONID");
		timeout = timeout == 0 ? 300000 : timeout;
	}
	
	public static class Session {
		private Map<String, String> userAttributes;
		private int level = 0;
		private long lastUse;
		private String userName;
		
		public synchronized boolean isAuthorized() {
			return level == 2;
		}
		public synchronized boolean isPreAuthorized() {
			return level == 1;
		}
		
		public synchronized Map<String, String> getUserAttributes() {
			return userAttributes;
		}
		
		public synchronized void clear() {
			level = 0;
			userAttributes = null;
		}
		
		public synchronized void preAuthorize(String userName, Map<String, String> userAttributes) {
			this.userName = userName;
			this.userAttributes = userAttributes;
			level = 1;
		}

		public synchronized void authorize() {
			level = 2;
		}
		
		public synchronized void touch() {
			lastUse = System.currentTimeMillis();
		}
		
		public synchronized long getLastUse() {
			return lastUse;
		}
		
		public synchronized String getUserName() {
			return userName;
		}
	}
	
	private String generateSessionID() {
		return UUID.randomUUID().toString();
	}
	
	public Session getSession(Request request) {
		String id = request.getHeader().getFirstCookie(cookieName);
		if (id == null) {
			return null;
		}
		Session s;
		synchronized (sessions) {
			s = sessions.get(id);
		}
		if (s != null) {
			s.touch();
		}
		return s;
	}
	
	public Session createSession(Exchange exc) {
		String id = generateSessionID();
		Session s = new Session();
		synchronized (sessions) {
			sessions.put(id, s);
		}
		exc.getResponse().getHeader().addCookieSession(cookieName, id + "; Path=/");
		return s;
	}
	
	public void cleanup() {
		long death = System.currentTimeMillis() - timeout;
		List<String> removeUs = new ArrayList<String>();
		synchronized (sessions) {
			for (Map.Entry<String, Session> e : sessions.entrySet())
				if (e.getValue().getLastUse() < death)
					removeUs.add(e.getKey());
			for (String sessionId : removeUs)
				sessions.remove(sessionId);
		}
	}

	public String getCookieName() {
		return cookieName;
	}

	public void setCookieName(String cookieName) {
		this.cookieName = cookieName;
	}

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

}
