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

import com.github.fge.jsonschema.core.keyword.syntax.checkers.common.ExclusiveMaximumSyntaxChecker;
import org.apache.commons.lang.StringUtils;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.authentication.session.CleanupThread.Cleaner;

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
	HashMap<String, Session> sessions = new HashMap<String, SessionManager.Session>();
	private final static String SESSION_ID = "SESSION_ID";
	private final static String SESSION = "SESSION";

	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
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
		String cookieValue = (String) exc.getProperty(SESSION_ID);
		if (cookieValue != null)
			exc.getResponse().getHeader().addCookieSession(cookieName, cookieValue);
	}

	public Session getOrCreateSession(Exchange exc) {
		Session s = getSession(exc);
		if (s == null) {
			s = createSession(exc);
		}
		return s;
	}

	public static class Session {
		private Map<String, String> userAttributes = new HashMap<String, String>();
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
			userAttributes = new HashMap<String, String>();
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

		public synchronized void clearCredentials() {
            getUserAttributes().remove("password");
            getUserAttributes().remove("client_secret");
        }
	}

	private String generateSessionID() {
		return UUID.randomUUID().toString();
	}

	public Session getSession(Exchange exc) {
		Session s = (Session) exc.getProperty(SESSION);
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
		String cookieValue = id + "; " +
				(domain != null ? "Domain=" + domain + "; " : "") +
				"Path=/" +
				(exc.getRule().getSslInboundContext() != null ? "; Secure" : "");
		if (exc.getResponse() == null)
			exc.setProperty(SESSION_ID, cookieValue);
		else
			exc.getResponse().getHeader().addCookieSession(cookieName, cookieValue);
		exc.setProperty(SESSION, s);
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
