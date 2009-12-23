package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;

public class ResponseContentLengthExchangeAccessor implements ExchangeAccessor {

	public static final String ID = "Response Content-Length";
	
	public Object get(Exchange exc) {
		if (exc == null || exc.getResponse() == null)
			return 0;
		return exc.getResponse().getHeader().getContentLength();
	}

	public String getId() {
		return ID;
	}

}
