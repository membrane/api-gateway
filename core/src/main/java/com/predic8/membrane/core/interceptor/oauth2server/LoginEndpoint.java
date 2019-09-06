package com.predic8.membrane.core.interceptor.oauth2server;

import com.bornium.http.Exchange;
import com.bornium.http.ResponseBuilder;
import com.bornium.security.oauth2openid.server.ServerServices;
import com.bornium.security.oauth2openid.server.endpoints.Endpoint;
import com.predic8.membrane.core.interceptor.authentication.session.LoginDialog;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.authentication.session.UserDataProvider;

public class LoginEndpoint extends Endpoint {

    LoginDialog2 loginDialog;

    public LoginEndpoint(ServerServices serverServices, UserDataProvider userDataProvider, com.predic8.membrane.core.interceptor.session.SessionManager sessionManager, String loginDialogLocation, String loginPath, String... paths) {
        super(serverServices, paths);
        loginDialog = new LoginDialog2(userDataProvider,null,sessionManager,null,loginDialogLocation,loginPath,true,null);
    }

    @Override
    public void invokeOn(Exchange exchange) throws Exception {
        com.predic8.membrane.core.exchange.Exchange exc = Convert.convertToMembraneExchange(exchange);
        loginDialog.handleLoginRequest(exc);
        exchange = Convert.convertFromMembraneExchange(exc);
    }

    @Override
    public String getScope(Exchange exchange) throws Exception {
        return null;
    }
}
