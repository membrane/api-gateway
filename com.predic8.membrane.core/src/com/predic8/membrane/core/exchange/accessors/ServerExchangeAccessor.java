package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;

public class ServerExchangeAccessor implements ExchangeAccessor {

	public static final String ID = "Server";
	
	public Object get(Exchange exc) {
		if (exc == null || exc.getRequestUri() == null)
			return "";
		return exc.getRequestUri();
	}

	public String getId() {
		return ID;
	}

}
