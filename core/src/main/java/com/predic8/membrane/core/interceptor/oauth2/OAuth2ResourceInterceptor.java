package com.predic8.membrane.core.interceptor.oauth2;

import java.math.BigInteger;
import java.net.URI;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;

import com.floreysoft.jmte.Engine;
import com.floreysoft.jmte.ErrorHandler;
import com.floreysoft.jmte.message.ParseException;
import com.floreysoft.jmte.token.Token;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager.Session;
import com.predic8.membrane.core.interceptor.server.WebServerInterceptor;
import com.predic8.membrane.core.util.URLParamUtil;
import com.predic8.membrane.core.util.URLUtil;

/**
 * @description Allows only authorized HTTP requests to pass through. Unauthorized requests get a redirect to the
 *              authorization server as response.
 * @topic 6. Security
 */
@MCElement(name="oauth2Resource")
public class OAuth2ResourceInterceptor extends AbstractInterceptor {
	private static Log log = LogFactory.getLog(OAuth2ResourceInterceptor.class.getName());
	
	private String loginLocation, loginPath = "/login/", publicURL;
	private AuthorizationService authorizationService;
	private SessionManager sessionManager;
	
	private final WebServerInterceptor wsi = new WebServerInterceptor();

	public String getLoginLocation() {
		return loginLocation;
	}

	/**
	 * @description location of the login dialog template (a directory containing the <i>index.html</i> file as well as possibly other resources)
	 * @example file:c:/work/login/
	 */
	@Required
	@MCAttribute
	public void setLoginLocation(String login) {
		this.loginLocation = login;
	}
	
	public String getLoginPath() {
		return loginPath;
	}
	
	/**
	 * @description context path of the login dialog
	 * @default /login/
	 */
	@MCAttribute
	public void setLoginPath(String loginPath) {
		this.loginPath = loginPath;
	}
	
	
	public String getPublicURL() {
		return publicURL;
	}
	
	@Required
	@MCAttribute
	public void setPublicURL(String publicURL) {
		this.publicURL = publicURL;
	}
	
	public AuthorizationService getAuthorizationService() {
		return authorizationService;
	}
	
	@Required
	@MCChildElement(order=10)
	public void setAuthorizationService(AuthorizationService authorizationService) {
		this.authorizationService = authorizationService;
	}
	
	public SessionManager getSessionManager() {
		return sessionManager;
	}
	
	@MCChildElement(order=20)
	public void setSessionManager(SessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}
	
	@Override
	public void init(Router router) throws Exception {
		super.init(router);
		
		authorizationService.init(router);
		
		if (sessionManager == null)
			sessionManager = new SessionManager();
		sessionManager.init(router);
		
		wsi.setDocBase(loginLocation);
		wsi.init(router);
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		
		if (isLoginRequest(exc)) {
			handleLoginRequest(exc);
			return Outcome.RETURN;
		}
		
		Session session = sessionManager.getSession(exc.getRequest());
		
		if (session == null)
			return respondWithRedirect(exc);
		
		if (session.isAuthorized()) {
			applyBackendAuthorization(exc, session);
			return Outcome.CONTINUE;
		}

		if (authorizationService.handleRequest(exc, session.getUserAttributes().get("state"), publicURL, session)) {
			if (exc.getResponse().getStatusCode() >= 400)
				session.clear();
			return Outcome.RETURN;
		}

		return respondWithRedirect(exc);
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

	private Outcome respondWithRedirect(Exchange exc) {
		exc.setResponse(Response.redirect(loginPath, false).build());
		return Outcome.RETURN;
	}

	
	
	
	
	
	
	
	public boolean isLoginRequest(Exchange exc) {
		URI uri = URI.create(exc.getRequest().getUri());
		return uri.getPath().startsWith(loginPath);
	}

	private void showPage(Exchange exc, String state, Object... params) throws Exception {
		String target = StringUtils.defaultString(URLParamUtil.getParams(exc).get("target"));
		
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
		model.put("loginPath", StringEscapeUtils.escapeXml(loginPath));
		String pathQuery = URLUtil.getPathFromPathQuery(URLUtil.getPathQuery(exc.getDestinations().get(0)));
		String url = authorizationService.getLoginURL(state, publicURL, pathQuery);
		model.put("loginURL", url);
		model.put("target", StringEscapeUtils.escapeXml(target));
		for (int i = 0; i < params.length; i+=2)
			model.put((String)params[i], params[i+1]);
		
		exc.getResponse().setBodyContent(engine.transform(exc.getResponse().getBody().toString(), model).getBytes(Constants.UTF_8_CHARSET));
	}

	public void handleLoginRequest(Exchange exc) throws Exception {
		Session s = sessionManager.getSession(exc.getRequest());
		
		String uri = exc.getRequest().getUri().substring(loginPath.length()-1);
		if (uri.indexOf('?') >= 0)
			uri = uri.substring(0, uri.indexOf('?'));
		exc.getDestinations().set(0, uri);
		
		if (uri.equals("/logout")) {
			if (s != null)
				s.clear();
			exc.setResponse(Response.redirect("/", false).build());
		} else if (uri.equals("/")) { 
			if (s == null || !s.isAuthorized()) {
				String state = new BigInteger(130, new SecureRandom()).toString(32);
				showPage(exc, state);

				Session session = sessionManager.createSession(exc);
				
				HashMap<String, String> userAttributes = new HashMap<String, String>();
				userAttributes.put("state", state);
				session.preAuthorize("", userAttributes);
			} else {
				showPage(exc, s.getUserAttributes().get("state"));
			}
		} else {
			wsi.handleRequest(exc);
		}
	}

}
