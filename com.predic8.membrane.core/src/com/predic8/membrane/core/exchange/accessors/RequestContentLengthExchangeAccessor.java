package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;

public class RequestContentLengthExchangeAccessor implements ExchangeAccessor {

	public static final String ID = "Request Content-Length";
	
	public Object get(Exchange exc) {
		if (exc == null || exc.getRequest() == null)
			return -1;
		
		return exc.getRequest().getHeader().getContentLength();
	}

	public String getId() {
		return ID;
	}

}
