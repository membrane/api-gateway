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

import java.util.Map;

import javax.xml.stream.XMLStreamReader;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.config.ElementName;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager.Session;

@ElementName("requireSession")
public class RequireSessionInterceptor extends AbstractInterceptor {
	
	private String loginDir, loginPath;
	
	private UserDataProvider userDataProvider;
	private TokenProvider tokenProvider;
	private SessionManager sessionManager;
	private AccountBlocker accountBlocker;
	private LoginDialog loginDialog;
	
	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		super.parseAttributes(token);
		loginDir = token.getAttributeValue("", "loginDir");
		loginPath = token.getAttributeValue("", "loginPath");
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {
		if (child.equals("staticUserDataProvider")) {
			userDataProvider = new StaticUserDataProvider();
			((StaticUserDataProvider) userDataProvider).parse(token);
		} else if (child.equals("ldapUserDataProvider")) {
			userDataProvider = new LDAPUserDataProvider();
			((LDAPUserDataProvider) userDataProvider).parse(token);
		} else if (child.equals("accountBlocker")) {
			accountBlocker = new AccountBlocker();
			accountBlocker.parse(token);
		} else if (child.equals("totpTokenProvider")) {
			tokenProvider = new TOTPTokenProvider();
			new AbstractXmlElement() {}.parse(token);
		} else if (child.equals("emptyTokenProvider")) {
			tokenProvider = new EmptyTokenProvider();
			new AbstractXmlElement() {}.parse(token);
		} else if (child.equals("telekomSMSTokenProvider")) {
			tokenProvider = new TelekomSMSTokenProvider();
			((SMSTokenProvider)tokenProvider).parse(token);
		} else if (child.equals("sessionManager")) {
			sessionManager = new SessionManager();
			((SessionManager)sessionManager).parse(token);
		} else {
			super.parseChildren(token, child);
		}
	}
	
	@Override
	public void init() throws Exception {
		if (userDataProvider == null)
			throw new Exception("No userDataProvider configured. - Cannot work without one.");
		if (tokenProvider == null)
			throw new Exception("No tokenProvider configured. - Cannot work without one.");
		if (sessionManager == null)
			sessionManager = new SessionManager();
		loginDialog = new LoginDialog(userDataProvider, tokenProvider, sessionManager, accountBlocker, loginDir, loginPath);
	}

	public void init(Router router) throws Exception {
		super.init(router);
		loginDialog.init(router);
		sessionManager.init(router);
		new CleanupThread(sessionManager, accountBlocker).start();
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (loginDialog.isLoginRequest(exc)) {
			loginDialog.handleLoginRequest(exc);
			return Outcome.RETURN;
		}
		Session s = sessionManager.getSession(exc.getRequest());
		if (s == null || !s.isAuthorized()) {
			return loginDialog.redirectToLogin(exc);
		}
		
		applyBackendAuthorization(exc, s);
		return super.handleRequest(exc);
	}

	private void applyBackendAuthorization(Exchange exc, Session s) {
		Header h = exc.getRequest().getHeader();
		for (Map.Entry<String, String> e : s.getUserAttributes().entrySet())
			if (e.getKey().startsWith("header")) {
				String headerName = e.getKey().substring(6);
				h.removeFields(headerName);
				h.add(headerName, e.getValue());
			}
	}
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		Header header = exc.getResponse().getHeader();
		header.removeFields("Cache-Control");
		header.removeFields("Pragma");
		header.removeFields("Expires");
			
	    header.add("Expires", "Tue, 03 Jul 2001 06:00:00 GMT");
	    header.add("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
	    header.add("Cache-Control", "post-check=0, pre-check=0");
	    header.add("Pragma", "no-cache");
		
		return super.handleResponse(exc);
	}

}
