package com.predic8.membrane.core.interceptor.oauth2;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager.Session;

public abstract class AuthorizationService {

	public abstract void init(Router router);
	
	public abstract String getLoginURL(String securityToken, String publicURL, String pathQuery);

	public abstract boolean handleRequest(Exchange exc, String state, String publicURL, Session session);

}
