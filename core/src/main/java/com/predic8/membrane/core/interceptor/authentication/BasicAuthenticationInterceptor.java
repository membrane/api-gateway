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

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider.User;
import com.predic8.membrane.core.interceptor.authentication.session.UserDataProvider;
import com.predic8.membrane.core.util.HttpUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Required;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * @description Blocks requests which do not have the correct RFC 1945 basic authentication credentials (HTTP header "Authentication: Basic ....").
 * @topic 6. Security
 */
@MCElement(name="basicAuthentication")
public class BasicAuthenticationInterceptor extends AbstractInterceptor {

	private StaticUserDataProvider userDataProvider = new StaticUserDataProvider();

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

	private boolean validUser(Exchange exc) throws Exception {
		return userDataProvider.getUsersByName().containsKey(getUsername(exc)) &&
				userDataProvider.getUsersByName().get(getUsername(exc)).getPassword().equals(getPassword(exc));
	}

	private String getUsername(Exchange exc) throws Exception {
		return getAuthorizationHeaderDecoded(exc).split(":", 2)[0];
	}
	private String getPassword(Exchange exc) throws Exception {
		return getAuthorizationHeaderDecoded(exc).split(":", 2)[1];
	}

	private Outcome deny(Exchange exc) {
		exc.setResponse(Response.unauthorized("").
				header(HttpUtil.createHeaders(null, "WWW-Authenticate", "Basic realm=\"" + Constants.PRODUCT_NAME + " Authentication\"")).
				build());
		return Outcome.ABORT;
	}

	private boolean hasNoAuthorizationHeader(Exchange exc) {
		return exc.getRequest().getHeader().getFirstValue(Header.AUTHORIZATION)==null;
	}

	/**
	 * The "Basic" authentication scheme defined in RFC 2617 does not properly define how to treat non-ASCII characters.
	 */
	private String getAuthorizationHeaderDecoded(Exchange exc) throws Exception {
		String value = exc.getRequest().getHeader().getFirstValue(Header.AUTHORIZATION);
		return new String(Base64.decodeBase64(value.substring(6).getBytes(Constants.UTF_8_CHARSET)), Constants.UTF_8_CHARSET);
	}

	public List<User> getUsers() {
		return userDataProvider.getUsers();
	}

	public Map<String, User> getUsersByName() {
		return userDataProvider.getUsersByName();
	}

	/**
	 * @description A list of username/password combinations to accept.
	 */
	@Required
	@MCChildElement
	public void setUsers(List<User> users) throws UnsupportedEncodingException, NoSuchAlgorithmException {
		userDataProvider.setUsers(users);
	}

	public UserDataProvider getUserDataProvider() {
		return userDataProvider;
	}

	/**
	 * @description The <i>user data provider</i> verifying a combination of a username with a password.
	 */
	public void setUserDataProvider(StaticUserDataProvider userDataProvider) {
		this.userDataProvider = userDataProvider;
	}

	@Override
	public void init() throws Exception {
		//to not alter the interface of "BasicAuthenticationInterceptor" in the config file the "name" attribute is renamed to "username" in code
		for(User user : getUsers()){
			if(user.getAttributes().containsKey("name")){
				String username = user.getAttributes().get("name");
				user.getAttributes().remove("name");
				user.getAttributes().put("username", username);
			}
		}

		userDataProvider.getUsersByName().clear();
		for (User user : userDataProvider.getUsers())
			userDataProvider.getUsersByName().put(user.getUsername(), user);
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
		sb.append("Users: ");
		for (User user : userDataProvider.getUsers()) {
			sb.append(StringEscapeUtils.escapeHtml(user.getUsername()));
			sb.append(", ");
		}
		sb.delete(sb.length()-2, sb.length());
		sb.append("<br/>Passwords are not shown.");
		return sb.toString();
	}

}
