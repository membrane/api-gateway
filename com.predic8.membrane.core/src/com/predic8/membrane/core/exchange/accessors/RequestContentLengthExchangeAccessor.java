package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;

public class RequestContentLengthExchangeAccessor implements ExchangeAccessor {

	public static final String ID = "Request Content-Length";
	
	public String get(Exchange exc) {
		if (exc == null || exc.getRequest() == null)
			return "";
		return "" + exc.getRequest().getHeader().getContentLength();
	}

	public String getId() {
		return ID;
	}

}
