package com.predic8.membrane.core.interceptor.authentication.xen;

import com.predic8.membrane.core.exchange.Exchange;

public interface CredentialAccessor<LoginData> {

    LoginData getLogin(Exchange exchange);

    void replaceLogin(Exchange exchange, LoginData newLoginData);

}
