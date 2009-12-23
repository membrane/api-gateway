package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;

public class PathExchangeAccessor implements ExchangeAccessor {

	public static final String ID = "Path";
	
	public Object get(Exchange exc) {
		if (exc == null || exc.getRequest() == null)
			return "";
		return exc.getRequest().getUri();
	}

	public String getId() {
		return ID;
	}

}
