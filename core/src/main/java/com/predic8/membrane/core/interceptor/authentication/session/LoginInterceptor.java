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

import org.springframework.beans.factory.annotation.Required;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager.Session;

/**
 * @description <p>
 *              The <i>login</i> interceptor can be used to restrict and secure end user access to an arbitrary web
 *              application.
 *              </p>
 *              <p>
 *              Users firstly have to authenticate themselves against a directory server using a username and password.
 *              Secondly, a numeric token is then sent to the user's cell phone using a text message service. After
 *              token verification, access to the web application is granted for the user's session. Single Sign On can
 *              easily be realized using a small source code extension or modification of a web application.
 *              </p>
 * @explanation <p>
 *              The <i>login</i> interceptor combines 4 modules to implement its functionality. One implementation of
 *              each of the 4 module types is required. (The <i>session manager</i> and <i>account blocker</i> have
 *              default implementations.)
 *              </p>
 *              <ul>
 *              <li>
 *              <p>
 *              The <i>user data provider</i> checks user passwords and provides additional data for each user (e.g.
 *              cell phone number, Single Sign On data, etc.).
 *              </p>
 *              </li>
 *              <li>
 *              <p>
 *              The <i>session manager</i> tracks the users' sessions across different HTTP requests (e.g. using a
 *              session cookie).
 *              </p>
 *              </li>
 *              <li>
 *              <p>
 *              The <i>account blocker</i> tracks the number of failed login attempts and might block future login
 *              attempts for a specified amount of time.
 *              </p>
 *              </li>
 *              <li>
 *              <p>
 *              The <i>token provider</i> generates the numeric token (possibly transmitting it to the user via a
 *              secondary channel like text messaging).
 *              </p>
 *              </li>
 *              </ul>
 *              <p>
 *              <img style="align:center; padding: 20px;" src="/images/doc/login.png" alt="login interceptor workflow"
 *              title="login interceptor workflow"/>
 *              </p>
 *              <p>
 *              (Whether text messages and LDAP is actually used depends on the configuration. Alternatives are
 *              possible.)
 *              </p>
 *              <p>
 *              The <i>login</i> interceptor realizes the login workflow. If all information entered by the user is
 *              valid, the workflow is as follows:
 *              </p>
 *              <ul>
 *              <li>The unauthenticated user is redirected to a login dialog.</li>
 *              <li>The user enters her username and password. (Step 1.)</li>
 *              <li>(A numeric token is sent to the user via text message, in case the <i>telekomSMSTokenProvider</i> is
 *              used. Steps 5 and 6.)</li>
 *              <li>The user enters her token. (Step 7.)</li>
 *              <li>The user is redirected to the originally requested URL (or a generic URL, in case the login dialog
 *              was directly requested). (Step 8.)</li>
 *              </ul>
 * @topic 6. Security
 */
@MCElement(name="login")
public class LoginInterceptor extends AbstractInterceptor {

	private String location, path, message;
	private boolean exposeUserCredentialsToSession;

	private UserDataProvider userDataProvider;
	private TokenProvider tokenProvider;
	private SessionManager sessionManager;
	private AccountBlocker accountBlocker;
	private LoginDialog loginDialog;

	@Override
	public void init() throws Exception {
		if (userDataProvider == null)
			throw new Exception("No userDataProvider configured. - Cannot work without one.");
		if (tokenProvider == null)
			throw new Exception("No tokenProvider configured. - Cannot work without one.");
		if (sessionManager == null)
			sessionManager = new SessionManager();
		userDataProvider.init(router);
		loginDialog = new LoginDialog(userDataProvider, tokenProvider, sessionManager, accountBlocker, location, path, exposeUserCredentialsToSession, message);
	}

	@Override
	public void init(Router router) throws Exception {
		super.init(router);
		tokenProvider.init(router);
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
		if (getId() != null)
			exc.setProperty(getId() + "-session", s);
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
		header.setNoCacheResponseHeaders();
		return super.handleResponse(exc);
	}

	public String getLocation() {
		return location;
	}

	/**
	 * @description location of the login dialog template (a directory containing the <i>index.html</i> file as well as possibly other resources)
	 * @example file:c:/work/login/
	 */
	@Required
	@MCAttribute
	public void setLocation(String location) {
		this.location = location;
	}

	public String getPath() {
		return path;
	}

	/**
	 * @description context path of the login dialog
	 * @example /login/
	 */
	@Required
	@MCAttribute
	public void setPath(String path) {
		this.path = path;
	}

	public UserDataProvider getUserDataProvider() {
		return userDataProvider;
	}

	/**
	 * @description The <i>user data provider</i> verifying a combination of a username with a password.
	 */
	@Required
	@MCChildElement(order=1)
	public void setUserDataProvider(UserDataProvider userDataProvider) {
		this.userDataProvider = userDataProvider;
	}

	public TokenProvider getTokenProvider() {
		return tokenProvider;
	}

	/**
	 * @description The <i>token provider</i> computing or generating a numeric value used for <a
	 *              href="http://en.wikipedia.org/wiki/Two_Factor_Authentication">two-factor authentication</a>.
	 */
	@Required
	@MCChildElement(order=4)
	public void setTokenProvider(TokenProvider tokenProvider) {
		this.tokenProvider = tokenProvider;
	}

	public SessionManager getSessionManager() {
		return sessionManager;
	}

	/**
	 * @description The <i>sessionManager</i>. (Default values will be used, if the element is not specified.)
	 */
	@MCChildElement(order=2)
	public void setSessionManager(SessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}

	public AccountBlocker getAccountBlocker() {
		return accountBlocker;
	}

	/**
	 * @description The <i>accountBlocker</i>. (Default values will be used, if the element is not specified.)
	 */
	@MCChildElement(order=3)
	public void setAccountBlocker(AccountBlocker accountBlocker) {
		this.accountBlocker = accountBlocker;
	}

	public boolean isExposeUserCredentialsToSession() {
		return exposeUserCredentialsToSession;
	}

	/**
	 * @description Whether the user's credentials should be copied over to the session. This means they
	 * will stay in memory and will be available to all Membrane components.
	 */
	@MCAttribute
	public void setExposeUserCredentialsToSession(boolean exposeUserCredentialsToSession) {
		this.exposeUserCredentialsToSession = exposeUserCredentialsToSession;
	}

	public String getMessage() {
		return message;
	}

	/**
	 * @description Set the message displayed during redirect.
	 */
	@MCAttribute
	public void setMessage(String message) {
		this.message = message;
	}
}
