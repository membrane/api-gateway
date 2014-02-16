/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.LogInterceptor;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager.Session;
import com.predic8.membrane.core.rules.NullRule;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.util.URLParamUtil;
import com.predic8.membrane.core.util.URLUtil;
import com.predic8.membrane.core.util.Util;

/**
 * @description Together with the {@link OAuth2ResourceInterceptor}, implements request authentication via OAuth2 using
 *              Google's Authorization Servers.
 * @explanation See the <a
 *              href="https://httprouter.wordpress.com/2013/07/22/protect-your-rest-resources-using-membrane-s-oauth2-feature/"
 *              >Membrane Blog</a> for a brief tutorial.
 */
@MCElement(name="google", topLevel=false)
public class GoogleAuthorizationService extends AuthorizationService {
	
	private static Log log = LogFactory.getLog(GoogleAuthorizationService.class.getName());

	// properties
	private String clientId;
	private String clientSecret;
	private HttpClientConfiguration httpClientConfiguration;
	
	// fields
	private HttpClient httpClient;
	private JsonFactory factory;
	private GoogleIdTokenVerifier verifier;
	
	public String getClientId() {
		return clientId;
	}
	
	@Required
	@MCAttribute
	public void setClientId(String clientId) {
		this.clientId = clientId;
	}
	
	public String getClientSecret() {
		return clientSecret;
	}
	
	@Required
	@MCAttribute
	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
	}
	
	public HttpClientConfiguration getHttpClientConfiguration() {
		return httpClientConfiguration;
	}
	
	@MCAttribute
	public void setHttpClientConfiguration(
			HttpClientConfiguration httpClientConfiguration) {
		this.httpClientConfiguration = httpClientConfiguration;
	}
	
	public void init(Router router) {
		httpClient = httpClientConfiguration == null ? router.getResolverMap()
				.getHTTPSchemaResolver().getHttpClient() : new HttpClient(
				httpClientConfiguration);

		factory = new JacksonFactory();
		verifier = new GoogleIdTokenVerifier(new ApacheHttpTransport(), factory);
	}
	
	@Override
	public String getLoginURL(String securityToken, String publicURL, String pathQuery) {
		return "https://accounts.google.com/o/oauth2/auth?"+
				"client_id=" + clientId + ".apps.googleusercontent.com&"+
				"response_type=code&"+
				"scope=openid%20email&"+
				"redirect_uri=" + publicURL + "oauth2callback&"+
				"state=security_token%3D" + securityToken + "%26url%3D" + pathQuery
				//+"&login_hint=jsmith@example.com"
				;
	}
	
	@Override
	public boolean handleRequest(Exchange exc, String state, String publicURL, Session session) {
		String path = URLUtil.getPathFromPathQuery(URLUtil.getPathQuery(exc.getDestinations().get(0)));
		
		if ("/oauth2callback".equals(path)) {
			
			try {
				Map<String, String> params = URLParamUtil.getParams(exc);
				
				String state2 = params.get("state");

				if (state2 == null)
					throw new RuntimeException("No CSRF token.");

				Map<String, String> param = URLParamUtil.parseQueryString(state2);
				
				if (param == null || !param.containsKey("security_token"))
					throw new RuntimeException("No CSRF token.");
				
				if (!param.get("security_token").equals(state))
					throw new RuntimeException("CSRF token mismatch.");
				
				String url = param.get("url");
				if (url == null)
					url = "/";
				
				if (log.isDebugEnabled())
					log.debug("CSRF token match.");
				
				String code = params.get("code");
				if (code == null)
					throw new RuntimeException("No code received.");

				Exchange e = new Request.Builder()
						.method("POST")
						.url("https://accounts.google.com/o/oauth2/token")
						.header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
						.body(
								"code=" + code + "&client_id=" + clientId
										+ ".apps.googleusercontent.com&client_secret="
										+ clientSecret + "&" + "redirect_uri=" + publicURL
										+ "oauth2callback&grant_type=authorization_code").buildExchange();
				e.setRule(new NullRule() {
					@Override
					public SSLContext getSslOutboundContext() {
						return new SSLContext(new SSLParser(), null, null);
					}
				});
				
				LogInterceptor logi = null;
				if (log.isDebugEnabled()) {
					logi = new LogInterceptor();
					logi.setHeaderOnly(false);
					logi.handleRequest(e);
				}
				
				Response response = httpClient.call(e).getResponse();
				
				if (response.getStatusCode() != 200) {
					response.getBody().read();
					throw new RuntimeException("Google Authentication server returned " + response.getStatusCode() + ".");
				}
				
				if (log.isDebugEnabled())
					logi.handleResponse(e);
				
				HashMap<String, String> json = Util.parseSimpleJSONResponse(response);
				
				if (!json.containsKey("id_token"))
					throw new RuntimeException("No id_token received.");
				
				GoogleIdToken idToken = GoogleIdToken.parse(factory, json.get("id_token"));
				if (idToken == null)
					throw new RuntimeException("Token cannot be parsed");

				if (!verifier.verify(idToken) ||
						!idToken.verifyAudience(Collections.singletonList(clientId + ".apps.googleusercontent.com")))
					throw new RuntimeException("Invalid token");

				Map<String, String> userAttributes = session.getUserAttributes();
				synchronized (userAttributes) {
					userAttributes.put("headerX-Authenticated-Email", idToken.getPayload().getEmail());
				}
				session.authorize();

				exc.setResponse(Response.redirect(url, false).build());
				return true;
			} catch (Exception e) {
				exc.setResponse(Response.badRequest().body(e.getMessage()).build());
			}
		}
		return false;
	}

	
	
}
