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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.LogInterceptor;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager.Session;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import com.predic8.membrane.core.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;

import java.util.HashMap;
import java.util.Map;

/**
 * @description Together with the {@link OAuth2ResourceInterceptor}, implements request authentication via OAuth2 using
 *              Github's Authorization Servers.
 */
@MCElement(name="github", topLevel=false)
public class GithubAuthorizationService extends AuthorizationService {

	private static Log log = LogFactory.getLog(GithubAuthorizationService.class.getName());

	// properties
	private String clientId;
	private String clientSecret;
	private HttpClientConfiguration httpClientConfiguration;
	private String appName;

	// fields
	private HttpClient httpClient;
	private URIFactory uriFactory;

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

	public String getAppName() {
		return appName;
	}

	@Required
	@MCAttribute
	public void setAppName(String appName) {
		this.appName = appName;
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

	@Override
	public void init(Router router) {
		httpClient = httpClientConfiguration == null ? router.getResolverMap()
				.getHTTPSchemaResolver().getHttpClient() : new HttpClient(
						httpClientConfiguration);

		uriFactory = router.getUriFactory();
	}

	@Override
	public String getLoginURL(String securityToken, String publicURL, String pathQuery) {
		return "https://github.com/login/oauth/authorize?"+
				"client_id=" + clientId + "&"+
				"response_type=code&"+
				"scope=&"+
				"redirect_uri=" + publicURL + "oauth2callback&"+
				"state=security_token%3D" + securityToken + "%26url%3D" + pathQuery
				//+"&login_hint=jsmith@example.com"
				;
	}

	@Override
	public boolean handleRequest(Exchange exc, String state, String publicURL, Session session) throws Exception {
		String path = uriFactory.create(exc.getDestinations().get(0)).getPath();

		if ("/oauth2callback".equals(path)) {

			try {
				Map<String, String> params = URLParamUtil.getParams(uriFactory, exc);

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
				.post("https://github.com/login/oauth/access_token")
				.header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
						.header(Header.ACCEPT, "application/json")
						.body(
								"code=" + code + "&client_id=" + clientId
										+ "&client_secret="
										+ clientSecret + "&" + "redirect_uri=" + publicURL
										+ "oauth2callback&grant_type=authorization_code").buildExchange();

				LogInterceptor logi = null;
				if (log.isDebugEnabled()) {
					logi = new LogInterceptor();
					logi.setHeaderOnly(false);
					logi.handleRequest(e);
				}

				Response response = httpClient.call(e).getResponse();

				if (response.getStatusCode() != 200) {
					response.getBody().read();
					throw new RuntimeException("Github Authentication server returned " + response.getStatusCode() + ".");
				}

				if (log.isDebugEnabled())
					logi.handleResponse(e);


				HashMap<String, String> json = Util.parseSimpleJSONResponse(response);

				if (!json.containsKey("access_token"))
					throw new RuntimeException("No access_token received.");

				String token = (String) json.get("access_token"); // and also "scope": "", "token_type": "bearer"

				Exchange e2 = new Request.Builder()
						.get("https://api.github.com/user")
						.header("Authorization", "token " + token)
						.header("User-Agent", appName)
						.header(Header.ACCEPT, "application/json")
						.body(
								"code=" + code + "&client_id=" + clientId
										+ "&client_secret="
										+ clientSecret + "&" + "redirect_uri=" + publicURL
										+ "oauth2callback&grant_type=authorization_code").buildExchange();

				if (log.isDebugEnabled()) {
					logi.setHeaderOnly(false);
					logi.handleRequest(e);
				}

				Response response2 = httpClient.call(e2).getResponse();

				if (log.isDebugEnabled())
					logi.handleResponse(e2);

				if (response2.getStatusCode() != 200) {
					throw new RuntimeException("User data could not be retrieved.");
				}

				HashMap<String, String> json2 = Util.parseSimpleJSONResponse(response2);

				if (!json2.containsKey("login"))
					throw new RuntimeException("User object does not contain 'login' key.");

				Map<String, String> userAttributes = session.getUserAttributes();
				synchronized (userAttributes) {
					userAttributes.put("headerX-Authenticated-Login", json2.get("login"));
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
