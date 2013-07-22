package com.predic8.membrane.core.interceptor.oauth2;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Required;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager.Session;
import com.predic8.membrane.core.util.URLUtil;

@MCElement(name="oauth2Resource")
public class OAuth2ResourceInterceptor extends AbstractInterceptor {
	
	private String publicURL;
	private AuthorizationService authorizationService;
	private SessionManager sessionManager;

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
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		
		Session session = sessionManager.getSession(exc.getRequest());
		
		if (session == null || session.getUserAttributes() == null || !session.getUserAttributes().containsKey("state"))
			return respondWithLoginRedirect(exc);
		
		if (session.isAuthorized()) {
			applyBackendAuthorization(exc, session);
			return Outcome.CONTINUE;
		}

		if (authorizationService.handleRequest(exc, session.getUserAttributes().get("state"), publicURL, session)) {
			if (exc.getResponse().getStatusCode() >= 400)
				session.clear();
			return Outcome.RETURN;
		}

		return respondWithDenied(exc, session);
	}

	private Outcome respondWithDenied(Exchange exc, Session session) {
		session.clear();
		exc.setResponse(Response.ok().body("<html><body>Denied. <a href=\".\">Refresh</a> </body></html>").build());
		return Outcome.RETURN;
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

	private Outcome respondWithLoginRedirect(Exchange exc) {
		String state = new BigInteger(130, new SecureRandom()).toString(32);

		String pathQuery = URLUtil.getPathFromPathQuery(URLUtil.getPathQuery(exc.getDestinations().get(0)));
		String url = authorizationService.getLoginURL(state, publicURL, pathQuery);
		
		exc.setResponse(Response.ok().header(Header.CONTENT_TYPE, MimeType.TEXT_HTML_UTF8).
				body("<html><body>Click <a href=\"" + url + "\">here</a> to login.</body></html>").
				build());
		
		Session session = sessionManager.createSession(exc);
		
		HashMap<String, String> userAttributes = new HashMap<String, String>();
		userAttributes.put("state", state);
		session.preAuthorize("", userAttributes);
		
		return Outcome.RETURN;
	}

}
