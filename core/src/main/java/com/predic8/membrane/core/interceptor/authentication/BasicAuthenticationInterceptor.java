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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.springframework.web.util.HtmlUtils;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.HttpUtil;

@MCElement(name="basicAuthentication", xsd="" +
		"<xsd:sequence>\r\n" + 
		"	<xsd:element name=\"user\" minOccurs=\"0\" maxOccurs=\"unbounded\">\r\n" + 
		"		<xsd:complexType>\r\n" + 
		"			<xsd:sequence />\r\n" + 
		"			<xsd:attribute name=\"name\" type=\"xsd:string\" use=\"required\"/>\r\n" + 
		"			<xsd:attribute name=\"password\" type=\"xsd:string\" use=\"required\"/>\r\n" + 
		"		</xsd:complexType>\r\n" + 
		"	</xsd:element>\r\n" + 
		"</xsd:sequence>\r\n" + 
		"", generateParserClass=false)
public class BasicAuthenticationInterceptor extends AbstractInterceptor {

	private Map<String, String> users = new HashMap<String, String>();
	
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
		return users.containsKey(getUsername(exc)) && 
			   users.get(getUsername(exc)).equals(getPassword(exc));
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
		return exc.getRequest().getHeader().getFirstValue("Authorization")==null;
	}
	
	/**
	 * The "Basic" authentication scheme defined in RFC 2617 does not properly define how to treat non-ASCII characters.
	 */
	private String getAuthorizationHeaderDecoded(Exchange exc) throws Exception {
		String value = exc.getRequest().getHeader().getFirstValue(Header.AUTHORIZATION);
		return new String(Base64.decodeBase64(value.substring(6).getBytes(Constants.UTF_8_CHARSET)), Constants.UTF_8_CHARSET);
	}

	public Map<String, String> getUsers() {
		return users;
	}

	public void setUsers(Map<String, String> users) {
		this.users = users;
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
		for (String user : users.keySet()) {
			sb.append(HtmlUtils.htmlEscape(user));
			sb.append(", ");
		}
		sb.delete(sb.length()-2, sb.length());
		sb.append("<br/>Passwords are not shown.");
		return sb.toString();
	}
	
	@Override
	public String getHelpId() {
		return "basic-authentication";
	}

}
