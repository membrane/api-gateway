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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.authentication.session.CleanupThread.*;
import com.predic8.membrane.core.proxies.*;
import org.apache.commons.lang3.*;
import org.jetbrains.annotations.*;

import javax.xml.stream.*;
import java.util.*;

/**
 * @explanation <p>
 *              The Session Manager identifies users across HTTP requests using a session cookie.
 *              </p>
 *              <p>
 *              The name of the session cookie can be specified using the <i>cookieName</i> attribute. The default name
 *              is "<tt>SESSIONID</tt>".
 *              </p>
 *              <p>
 *              The session timeout can be specified in milliseconds using the <i>timeout</i> attribute. The default
 *              timeout is 5 minutes.
 *              </p>
 */
@MCElement(name="sessionManager", topLevel=false)
public class SessionManager extends AbstractXmlElement implements Cleaner {
	private String cookieName;
	private long timeout;
	private String domain;

	// TODO: bind session also to remote IP (for public Membrane release)
	protected final HashMap<String, Session> sessions = new HashMap<>();
	protected final static String SESSION_ID = "SESSION_ID";
	protected final static String SESSION = "SESSION";

	@Override
	protected void parseAttributes(XMLStreamReader token) {
		cookieName = token.getAttributeValue("", "cookieName");
		timeout = Long.parseLong(StringUtils.defaultIfEmpty(token.getAttributeValue("", "timeout"), "300000"));
		domain = token.getAttributeValue("", "domain");
	}

	public void init(Router router) {
		cookieName = StringUtils.defaultIfEmpty(cookieName, "SESSIONID");
		timeout = timeout == 0 ? 300000 : timeout;
	}

	/**
	 * Sets the Session Cookie on the response, if necessary (e.g. a session was created)
     */
	public void postProcess(Exchange exc) {
		String cookieValue = exc.getPropertyOrNull(SESSION_ID, String.class);
		if (cookieValue != null && exc.getResponse() != null)
			exc.getResponse().getHeader().addCookieSession(cookieName, cookieValue);
	}

	public Session getOrCreateSession(Exchange exc) {
		Session s = getSession(exc);
		if (s == null) {
			s = createSession(exc);
		}
		return s;
	}

    public void removeSession(Exchange exc) {
		String id = exc.getRequest().getHeader().getFirstCookie(cookieName);
		if(id != null) {
			synchronized (sessions) {
				sessions.remove(id);
			}
			return;
		}
		Session s = getSession(exc);
		removeSession(s);
    }

    public void removeSession(Session s){
		if(s != null){

			synchronized (sessions){
				List<String> remove = sessions.keySet().stream().filter(sId -> {
					Session other = sessions.get(sId);
					return s == other;
				}).toList();
				remove.forEach(sessions::remove);
			}
		}
	}

    public static class Session {
		private Map<String, String> userAttributes = new HashMap<>();
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

		public synchronized void setUserAttributes(Map<String, String> userAttributes) {
			this.userAttributes = userAttributes;
		}

		public synchronized void clear() {
			level = 0;
			userAttributes = new HashMap<>();
		}

		public synchronized void preAuthorize(String userName, Map<String, String> userAttributes) {
			this.userName = userName;
			this.userAttributes.putAll(userAttributes);
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

		protected synchronized void setUserName(String userName) {
			this.userName = userName;
		}

		public synchronized void clearCredentials() {
            getUserAttributes().remove("password");
            getUserAttributes().remove("client_secret");
        }

		protected synchronized int getLevel() {
			return level;
		}

		protected synchronized void setLevel(int level) {
			this.level = level;
		}
	}

	private String generateSessionID() {
		return UUID.randomUUID().toString();
	}

	public Session getSession(Exchange exc) {
		Session s = exc.getPropertyOrNull(SESSION, Session.class);
		if (s != null)
			return s;
		String id = exc.getRequest().getHeader().getFirstCookie(cookieName);
		if (id == null) {
			return null;
		}
		return getSession(id);
	}

	private Session getSession(String id){
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
		return createSession(exc,id);
	}

	private Session createSession(Exchange exc, String id)
	{
		Session s = new Session();
		synchronized (sessions) {
			sessions.put(id, s);
		}
		String cookieValue = getCookieValue(exc, id);

		exc.setProperty(SESSION_ID, cookieValue);
		exc.setProperty(SESSION, s);
		if (exc.getResponse() != null)
			exc.getResponse().getHeader().addCookieSession(cookieName, cookieValue);
		return s;
	}

	// TODO is that all for the value or is there something missing?
	protected @NotNull String getCookieValue(Exchange exc, String value) {
		return value + "; " +
			   (domain != null ? "Domain=" + domain + "; " : "") +
			   "Path=/" + getSecureString(exc);
	}

	protected static @NotNull String getSecureString(Exchange exc) {
		Proxy proxy = exc.getProxy();
		if (!(proxy instanceof SSLableProxy sp))
			return "";
		return sp.isInboundSSL() ? "; Secure" : "";
	}

	public void cleanup() {
		long death = System.currentTimeMillis() - timeout;
		List<String> removeUs = new ArrayList<>();
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

	@MCAttribute
	public void setCookieName(String cookieName) {
		this.cookieName = cookieName;
	}

	public long getTimeout() {
		return timeout;
	}

	@MCAttribute
	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	public String getDomain() {
		return domain;
	}

	@MCAttribute
	public void setDomain(String domain) {
		this.domain = domain;
	}
}
