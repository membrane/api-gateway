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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Required;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.HttpUtil;

/**
 * @description Blocks requests which do not have the correct RFC 1945 basic authentication credentials (HTTP header "Authentication: Basic ...."). 
 * @topic 6. Security
 */
@MCElement(name="basicAuthentication")
public class BasicAuthenticationInterceptor extends AbstractInterceptor {
	
	@MCElement(name="user", topLevel=false, id="basicAuthentication-user")
	public static class User {
		private String name, password;
		
		public User() {
		}
		
		public User(String name, String password) {
			setName(name);
			setPassword(password);
		}

		public String getName() {
			return name;
		}
		
		/**
		 * @description The user's login.
		 * @example admin
		 */
		@Required
		@MCAttribute
		public void setName(String name) {
			this.name = name;
		}
		
		public String getPassword() {
			return password;
		}
		
		/**
		 * @description The user's password.
		 * @example s3cr3t
		 */
		@Required
		@MCAttribute
		public void setPassword(String password) {
			this.password = password;
		}
	}

	private List<User> users = new ArrayList<User>();
	private Map<String, User> usersByName = new HashMap<String, User>();
	
	public BasicAuthenticationInterceptor() {
		name = "Basic Authenticator";		
		setFlow(Flow.Set.REQUEST);
	}
	
	public Outcome handleRequest(Exchange exc) throws Exception {
		
		if (hasNoAuthorizationHeader(exc) || !validUser(exc)) {
			return deny(exc);
		}
		
		return Outcome.CONTINUE;
	}

	private boolean validUser(Exchange exc) throws Exception {		
		return usersByName.containsKey(getUsername(exc)) && 
			   usersByName.get(getUsername(exc)).getPassword().equals(getPassword(exc));
	}

	private String getUsername(Exchange exc) throws Exception {
		return getAuthorizationHeaderDecoded(exc).split(":")[0];
	}
	private String getPassword(Exchange exc) throws Exception {
		return getAuthorizationHeaderDecoded(exc).split(":")[1];
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
		return users;
	}
	
	public Map<String, User> getUsersByName() {
		return usersByName;
	}

	/**
	 * @description A list of username/password combinations to accept.
	 */
	@Required
	@MCChildElement
	public void setUsers(List<User> users) {
		this.users = users;
	}
	
	@Override
	public void init() throws Exception {
		usersByName.clear();
		for (User user : users)
			usersByName.put(user.getName(), user);
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
		for (User user : users) {
			sb.append(StringEscapeUtils.escapeHtml(user.getName()));
			sb.append(", ");
		}
		sb.delete(sb.length()-2, sb.length());
		sb.append("<br/>Passwords are not shown.");
		return sb.toString();
	}
	
}
