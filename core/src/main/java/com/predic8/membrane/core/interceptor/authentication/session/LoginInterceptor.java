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

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCRaw;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.config.ElementName;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager.Session;

@ElementName("login")
@MCRaw(xsd="" +
		"	<xsd:group name=\"TokenProviderGroup\">\r\n" + 
		"		<xsd:choice>\r\n" + 
		"			<xsd:element name=\"staticUserDataProvider\">\r\n" + 
		"				<xsd:complexType>\r\n" + 
		"					<xsd:sequence>\r\n" + 
		"						<xsd:element name=\"user\" minOccurs=\"0\" maxOccurs=\"unbounded\">\r\n" + 
		"							<xsd:complexType>\r\n" + 
		"								<xsd:sequence />\r\n" + 
		"								<xsd:attribute name=\"username\" type=\"xsd:string\" />\r\n" + 
		"								<xsd:attribute name=\"password\" type=\"xsd:string\" />\r\n" + 
		"								<xsd:attribute name=\"sms\" type=\"xsd:string\" />\r\n" + 
		"								<xsd:attribute name=\"secret\" type=\"xsd:string\" />\r\n" + 
		"								<xsd:anyAttribute processContents=\"skip\" />\r\n" + 
		"							</xsd:complexType>\r\n" + 
		"						</xsd:element>\r\n" + 
		"					</xsd:sequence>\r\n" + 
		"				</xsd:complexType>\r\n" + 
		"			</xsd:element>\r\n" + 
		"			<xsd:element name=\"ldapUserDataProvider\">\r\n" + 
		"				<xsd:complexType>\r\n" + 
		"					<xsd:sequence>\r\n" + 
		"						<xsd:element name=\"map\">\r\n" + 
		"							<xsd:complexType>\r\n" + 
		"								<xsd:sequence>\r\n" + 
		"									<xsd:element name=\"attribute\" minOccurs=\"0\" maxOccurs=\"unbounded\">\r\n" + 
		"										<xsd:complexType>\r\n" + 
		"											<xsd:sequence />\r\n" + 
		"											<xsd:attribute name=\"from\" type=\"xsd:string\" use=\"required\" />\r\n" + 
		"											<xsd:attribute name=\"to\" type=\"xsd:string\" use=\"required\" />\r\n" + 
		"										</xsd:complexType>\r\n" + 
		"									</xsd:element>\r\n" + 
		"								</xsd:sequence>\r\n" + 
		"							</xsd:complexType>\r\n" + 
		"						</xsd:element>\r\n" + 
		"					</xsd:sequence>\r\n" + 
		"					<xsd:attribute name=\"url\" type=\"xsd:string\" use=\"required\" />\r\n" + 
		"					<xsd:attribute name=\"base\" type=\"xsd:string\" use=\"required\" />\r\n" + 
		"					<xsd:attribute name=\"binddn\" type=\"xsd:string\" />\r\n" + 
		"					<xsd:attribute name=\"bindpw\" type=\"xsd:string\" />\r\n" + 
		"					<xsd:attribute name=\"searchPattern\" type=\"xsd:string\" use=\"required\" />\r\n" + 
		"					<xsd:attribute name=\"searchScope\" type=\"xsd:string\" default=\"subtree\" />\r\n" + 
		"					<xsd:attribute name=\"timeout\" type=\"xsd:string\" default=\"1000\" />\r\n" + 
		"					<xsd:attribute name=\"connectTimeout\" type=\"xsd:string\" default=\"1000\" />\r\n" + 
		"					<xsd:attribute name=\"readAttributesAsSelf\" type=\"xsd:boolean\" default=\"true\" />\r\n" + 
		"					<xsd:attribute name=\"passwordAttribute\" type=\"xsd:string\" />\r\n" + 
		"				</xsd:complexType>\r\n" + 
		"			</xsd:element>\r\n" + 
		"			<xsd:element name=\"unifyingUserDataProvider\">\r\n" + 
		"				<xsd:complexType>\r\n" + 
		"					<xsd:sequence minOccurs=\"0\" maxOccurs=\"unbounded\">\r\n" + 
		"						<xsd:group ref=\"TokenProviderGroup\" />\r\n" + 
		"					</xsd:sequence>\r\n" + 
		"				</xsd:complexType>\r\n" + 
		"			</xsd:element>\r\n" + 
		"		</xsd:choice>\r\n" + 
		"	</xsd:group>\r\n" + 
		"")
@MCElement(name="login", xsd="" +
		"			<xsd:sequence>\r\n" + 
		"				<xsd:group ref=\"TokenProviderGroup\" />\r\n" + 
		"				\r\n" + 
		"				<xsd:element minOccurs=\"0\" name=\"sessionManager\">\r\n" + 
		"					<xsd:complexType>\r\n" + 
		"						<xsd:sequence />\r\n" + 
		"						<xsd:attribute name=\"cookieName\" type=\"xsd:string\" default=\"SESSIONID\" />\r\n" + 
		"						<xsd:attribute name=\"timeout\" type=\"xsd:long\" default=\"300000\"/>\r\n" + 
		"						<xsd:attribute name=\"domain\" type=\"xsd:string\" />\r\n" + 
		"					</xsd:complexType>\r\n" + 
		"				</xsd:element>\r\n" + 
		"\r\n" + 
		"				<xsd:element minOccurs=\"0\" name=\"accountBlocker\">\r\n" + 
		"					<xsd:complexType>\r\n" + 
		"						<xsd:sequence />\r\n" + 
		"						<xsd:attribute name=\"afterFailedLogins\" type=\"xsd:int\" default=\"5\" />\r\n" + 
		"						<xsd:attribute name=\"afterFailedLoginsWithin\" type=\"xsd:long\" default=\"9223372036854775807\"/>\r\n" + 
		"						<xsd:attribute name=\"blockFor\" type=\"xsd:long\" default=\"3600000\"/>\r\n" + 
		"						<xsd:attribute name=\"blockWholeSystemAfter\" type=\"xsd:int\" default=\"1000000\"/>\r\n" + 
		"					</xsd:complexType>\r\n" + 
		"				</xsd:element>\r\n" + 
		"				\r\n" + 
		"				<xsd:choice> <!-- one of the token providers -->\r\n" + 
		"				\r\n" + 
		"					<xsd:element name=\"emptyTokenProvider\" />\r\n" + 
		"					\r\n" + 
		"					<xsd:element name=\"totpTokenProvider\" />\r\n" + 
		"					\r\n" + 
		"					<xsd:element name=\"telekomSMSTokenProvider\">\r\n" + 
		"						<xsd:complexType>\r\n" + 
		"							<xsd:sequence />\r\n" + 
		"							<xsd:attribute name=\"user\" type=\"xsd:string\" use=\"required\" />\r\n" + 
		"							<xsd:attribute name=\"password\" type=\"xsd:string\" use=\"required\" />\r\n" + 
		"							<xsd:attribute name=\"simulate\" type=\"xsd:boolean\" default=\"false\" />\r\n" + 
		"							<xsd:attribute name=\"prefixText\" type=\"xsd:string\" default=\"Token: \" />\r\n" + 
		"							<xsd:attribute name=\"normalizeTelephoneNumber\" type=\"xsd:boolean\" default=\"false\"/>\r\n" + 
		"						</xsd:complexType>\r\n" + 
		"					</xsd:element>\r\n" + 
		"					\r\n" + 
		"				</xsd:choice>\r\n" + 
		"			</xsd:sequence>\r\n" + 
		"			<xsd:attribute name=\"path\" type=\"xsd:string\" use=\"required\" />\r\n" + 
		"			<xsd:attribute name=\"location\" type=\"xsd:string\" use=\"required\" />\r\n" + 
		"")
public class LoginInterceptor extends AbstractInterceptor {
	
	private String location, path;
	
	private UserDataProvider userDataProvider;
	private TokenProvider tokenProvider;
	private SessionManager sessionManager;
	private AccountBlocker accountBlocker;
	private LoginDialog loginDialog;
	
	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		super.parseAttributes(token);
		location = token.getAttributeValue("", "location");
		path = token.getAttributeValue("", "path");
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {
		if (child.equals("staticUserDataProvider")) {
			userDataProvider = new StaticUserDataProvider();
			((StaticUserDataProvider) userDataProvider).parse(token);
		} else if (child.equals("ldapUserDataProvider")) {
			userDataProvider = new LDAPUserDataProvider();
			((LDAPUserDataProvider) userDataProvider).parse(token);
		} else if (child.equals("unifyingUserDataProvider")) {
			userDataProvider = new UnifyingUserDataProvider();
			((UnifyingUserDataProvider) userDataProvider).parse(token);
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
		loginDialog = new LoginDialog(userDataProvider, tokenProvider, sessionManager, accountBlocker, location, path);
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

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public UserDataProvider getUserDataProvider() {
		return userDataProvider;
	}

	public void setUserDataProvider(UserDataProvider userDataProvider) {
		this.userDataProvider = userDataProvider;
	}

	public TokenProvider getTokenProvider() {
		return tokenProvider;
	}

	public void setTokenProvider(TokenProvider tokenProvider) {
		this.tokenProvider = tokenProvider;
	}

	public SessionManager getSessionManager() {
		return sessionManager;
	}

	public void setSessionManager(SessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}

	public AccountBlocker getAccountBlocker() {
		return accountBlocker;
	}

	public void setAccountBlocker(AccountBlocker accountBlocker) {
		this.accountBlocker = accountBlocker;
	}

}
