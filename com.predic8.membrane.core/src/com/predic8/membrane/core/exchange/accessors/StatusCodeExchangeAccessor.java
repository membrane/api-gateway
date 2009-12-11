package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;

public class StatusCodeExchangeAccessor implements ExchangeAccessor {

	public static final String ID = "Status-Code";
	
	public String get(Exchange exc) {
		if (exc == null || exc.getResponse() == null)
			return "";
		return Integer.toString(exc.getResponse().getStatusCode());
	}

	public String getId() {
		return ID;
	}

}
