package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;

public class ResponseContentLengthExchangeAccessor implements ExchangeAccessor {

	public static final String ID = "Response Content-Length";
	
	public String get(Exchange exc) {
		if (exc == null || exc.getResponse() == null)
			return "";
		return "" + exc.getResponse().getHeader().getContentLength();
	}

	public String getId() {
		return ID;
	}

}
