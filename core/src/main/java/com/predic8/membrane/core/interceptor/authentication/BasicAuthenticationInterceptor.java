/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.authentication;

import com.google.common.collect.ImmutableMap;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider.User;
import com.predic8.membrane.core.interceptor.authentication.session.UserDataProvider;
import com.predic8.membrane.core.util.HttpUtil;

import java.util.List;
import java.util.NoSuchElementException;

import static com.predic8.membrane.core.Constants.PRODUCT_NAME;
import static com.predic8.membrane.core.http.Header.AUTHORIZATION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.codec.binary.Base64.decodeBase64;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

/**
 * @description Blocks requests which do not have the correct RFC 1945 basic authentication credentials (HTTP header "Authentication: Basic ....").
 * @topic 6. Security
 */
@MCElement(name="basicAuthentication")
public class BasicAuthenticationInterceptor extends AbstractInterceptor {

	private UserDataProvider userDataProvider = new StaticUserDataProvider();

	public BasicAuthenticationInterceptor() {
		name = "Basic Authenticator";
		setFlow(Flow.Set.REQUEST);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {

		if (hasNoAuthorizationHeader(exc) || !validUser(exc)) {
			return deny(exc);
		}

		return Outcome.CONTINUE;
	}

	private boolean validUser(Exchange exc) {
		try {
			userDataProvider.verify(ImmutableMap.of(
					"username", getUsername(exc),
					"password", getPassword(exc)
			));
			return true;
		} catch (NoSuchElementException e) {
			return false;
		}
	}

	private String getUsername(Exchange exc) {
		return getAuthorizationHeaderDecoded(exc).split(":", 2)[0];
	}
	private String getPassword(Exchange exc) {
		return getAuthorizationHeaderDecoded(exc).split(":", 2)[1];
	}

	private Outcome deny(Exchange exc) {
		exc.setResponse(Response.unauthorized("").
				header(HttpUtil.createHeaders(null, "WWW-Authenticate", "Basic realm=\"" + PRODUCT_NAME + " Authentication\"")).
				build());
		return Outcome.ABORT;
	}

	private boolean hasNoAuthorizationHeader(Exchange exc) {
		return exc.getRequest().getHeader().getFirstValue(AUTHORIZATION) == null;
	}

	/**
	 * The "Basic" authentication scheme defined in RFC 2617 does not properly define how to treat non-ASCII characters.
	 */
	private String getAuthorizationHeaderDecoded(Exchange exc) {
		String value = exc.getRequest().getHeader().getFirstValue(AUTHORIZATION);
		return new String(decodeBase64(value.substring(6).getBytes(UTF_8)), UTF_8);
	}

	public List<User> getUsers() {
		if (userDataProvider instanceof StaticUserDataProvider sud) {
			return sud.getUsers();
		}
		throw new UnsupportedOperationException("getUsers not implemented for this userDataProvider.");
	}

	/**
	 * @description A list of username/password combinations to accept.
	 */
	@MCChildElement(order = 20)
	public void setUsers(List<User> users) {
		((StaticUserDataProvider)userDataProvider).setUsers(users);
	}

	public UserDataProvider getUserDataProvider() {
		return userDataProvider;
	}

	/**
	 * @description The <i>user data provider</i> verifying a combination of a username with a password.
	 */
	@MCChildElement(order = 10)
	public void setUserDataProvider(UserDataProvider userDataProvider) {
		this.userDataProvider = userDataProvider;
	}

	@Override
	public void init(Router router) throws Exception {
		//to not alter the interface of "BasicAuthenticationInterceptor" in the config file the "name" attribute is renamed to "username" in code
		if (userDataProvider instanceof StaticUserDataProvider)
			for(User user : getUsers()){
				if(user.getAttributes().containsKey("name")){
					String username = user.getAttributes().get("name");
					user.getAttributes().remove("name");
					user.getAttributes().put("username", username);
				}
			}

		userDataProvider.init(router);
	}

	@Override
	public String getShortDescription() {
		return "Authenticates incoming requests based on a fixed user list.";
	}

	@Override
	public String getLongDescription() {
		StringBuilder sb = new StringBuilder();
		sb.append(getShortDescription());
		sb.append("<br/>");
		if (userDataProvider instanceof StaticUserDataProvider) {
			sb.append("Users: ");
			for (User user : ((StaticUserDataProvider)userDataProvider).getUsers()) {
				sb.append(escapeHtml4(user.getUsername()));
				sb.append(", ");
			}
			sb.delete(sb.length()-2, sb.length());
			sb.append("<br/>Passwords are not shown.");
		}
		return sb.toString();
	}

}
