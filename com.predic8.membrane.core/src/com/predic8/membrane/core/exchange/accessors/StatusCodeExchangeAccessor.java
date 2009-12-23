package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;

public class StatusCodeExchangeAccessor implements ExchangeAccessor {

	public static final String ID = "Status-Code";
	
	public Object get(Exchange exc) {
		if (exc == null || exc.getResponse() == null)
			return 0;
		return exc.getResponse().getStatusCode();
	}

	public String getId() {
		return ID;
	}

}
