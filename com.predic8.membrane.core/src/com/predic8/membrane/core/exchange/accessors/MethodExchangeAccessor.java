package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;

public class MethodExchangeAccessor implements ExchangeAccessor {

	public static final String ID = "Method";
	
	public Object get(Exchange exc) {
		if (exc == null || exc.getRequest() == null)
			return "";
		return exc.getRequest().getMethod();
	}

	public String getId() {
		return ID;
	}

}
