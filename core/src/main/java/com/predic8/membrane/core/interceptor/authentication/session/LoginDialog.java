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

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.floreysoft.jmte.Engine;
import com.floreysoft.jmte.ErrorHandler;
import com.floreysoft.jmte.message.ParseException;
import com.floreysoft.jmte.token.Token;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager.Session;
import com.predic8.membrane.core.interceptor.server.WebServerInterceptor;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.URI;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;

public class LoginDialog {
	private static Log log = LogFactory.getLog(LoginDialog.class.getName());

	private String path, message;
	private boolean exposeUserCredentialsToSession;
	private URIFactory uriFactory;

	private final UserDataProvider userDataProvider;
	private final TokenProvider tokenProvider;
	private final SessionManager sessionManager;
	private final AccountBlocker accountBlocker;

	private final WebServerInterceptor wsi;

	public LoginDialog(
			UserDataProvider userDataProvider,
			TokenProvider tokenProvider,
			SessionManager sessionManager,
			AccountBlocker accountBlocker,
			String dialogLocation,
			String path,
			boolean exposeUserCredentialsToSession,
			String message) {
		this.path = path;
		this.exposeUserCredentialsToSession = exposeUserCredentialsToSession;
		this.userDataProvider = userDataProvider;
		this.tokenProvider = tokenProvider;
		this.sessionManager = sessionManager;
		this.accountBlocker = accountBlocker;
		this.message = message;

		wsi = new WebServerInterceptor();
		wsi.setDocBase(dialogLocation);
	}

	public void init(Router router) throws Exception {
		uriFactory = router.getUriFactory();
		router.getResolverMap().resolve(ResolverMap.combine(router.getBaseLocation(), wsi.getDocBase(), "index.html")).close();
		wsi.init(router);
	}

	public boolean isLoginRequest(Exchange exc) {
		URI uri = uriFactory.createWithoutException(exc.getRequest().getUri());
		return uri.getPath().startsWith(path);
	}

	private void showPage(Exchange exc, int page, Object... params) throws Exception {
		String target = StringUtils.defaultString(URLParamUtil.getParams(uriFactory, exc).get("target"));

		exc.getDestinations().set(0, "/index.html");
		wsi.handleRequest(exc);

		Engine engine = new Engine();
		engine.setErrorHandler(new ErrorHandler() {

			@Override
			public void error(String arg0, Token arg1, Map<String, Object> arg2) throws ParseException {
				log.error(arg0);
			}

			@Override
			public void error(String arg0, Token arg1) throws ParseException {
				log.error(arg0);
			}
		});
		Map<String, Object> model = new HashMap<String, Object>();
		model.put("action", StringEscapeUtils.escapeXml(path));
		model.put("target", StringEscapeUtils.escapeXml(target));
		if (page == 1)
			model.put("token", true);
		for (int i = 0; i < params.length; i+=2)
			model.put((String)params[i], params[i+1]);

		exc.getResponse().setBodyContent(engine.transform(exc.getResponse().getBody().toString(), model).getBytes(Constants.UTF_8_CHARSET));
	}

	public void handleLoginRequest(Exchange exc) throws Exception {
		Session s = sessionManager.getSession(exc.getRequest());

		String uri = exc.getRequest().getUri().substring(path.length()-1);
		if (uri.indexOf('?') >= 0)
			uri = uri.substring(0, uri.indexOf('?'));
		exc.getDestinations().set(0, uri);

		if (uri.equals("/logout")) {
			if (s != null)
				s.clear();
			exc.setResponse(Response.redirect(path, false).body("").build());
		} else if (uri.equals("/")) {
			if (s == null || !s.isPreAuthorized()) {
				if (exc.getRequest().getMethod().equals("POST")) {
					Map<String, String> userAttributes;
					Map<String, String> params = URLParamUtil.getParams(uriFactory, exc);
					String username = params.get("username");
					if (username == null) {
						showPage(exc, 0, "error", "INVALID_PASSWORD");
						return;
					}
					if (accountBlocker != null && accountBlocker.isBlocked(username)) {
						showPage(exc, 0, "error", "ACCOUNT_BLOCKED");
						return;
					}
					try {
						userAttributes = userDataProvider.verify(params);
					} catch (NoSuchElementException e) {
						if (accountBlocker != null)
							accountBlocker.fail(username);
						showPage(exc, 0, "error", "INVALID_PASSWORD");
						return;
					} catch (Exception e) {
						log.error(e);
						showPage(exc, 0, "error", "INTERNAL_SERVER_ERROR");
						return;
					}
					if (exposeUserCredentialsToSession) {
						for (Map.Entry<String, String> param : params.entrySet())
							if (!userAttributes.containsKey(param.getKey()))
								userAttributes.put(param.getKey(), param.getValue());
					}
					showPage(exc, 1);
					sessionManager.createSession(exc).preAuthorize(username, userAttributes);
					tokenProvider.requestToken(userAttributes);
				} else {
					showPage(exc, 0);
				}
			} else {
				if (accountBlocker != null && accountBlocker.isBlocked(s.getUserName())) {
					showPage(exc, 0, "error", "ACCOUNT_BLOCKED");
					return;
				}
				if (exc.getRequest().getMethod().equals("POST")) {
					String token = URLParamUtil.getParams(uriFactory, exc).get("token");
					try {
						tokenProvider.verifyToken(s.getUserAttributes(), token);
					} catch (NoSuchElementException e) {
						if (accountBlocker != null)
							accountBlocker.fail(s.getUserName());
						s.clear();
						showPage(exc, 0, "error", "INVALID_TOKEN");
						return;
					} catch (Exception e) {
						log.error(e);
						s.clear();
						showPage(exc, 0, "error", "INTERNAL_SERVER_ERROR");
						return;
					}
					if (accountBlocker != null)
						accountBlocker.unblock(s.getUserName());
					String target = URLParamUtil.getParams(uriFactory, exc).get("target");
					if (StringUtils.isEmpty(target))
						target = "/";

					if (this.message != null)
						exc.setResponse(Response.redirectWithout300(target, message).build());
					else
						exc.setResponse(Response.redirectWithout300(target).build());

					s.authorize();
				} else {
					showPage(exc, 1);
				}
			}
		} else {
			wsi.handleRequest(exc);
		}
	}

	public Outcome redirectToLogin(Exchange exc) throws MalformedURLException, UnsupportedEncodingException {
		exc.setResponse(Response.
				redirect(path + "?target=" + URLEncoder.encode(exc.getOriginalRequestUri(), "UTF-8"), false).
				dontCache().
				body("").
				build());
		return Outcome.RETURN;
	}

}
