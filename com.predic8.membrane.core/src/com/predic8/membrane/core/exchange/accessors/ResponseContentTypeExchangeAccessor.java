package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;

public class ResponseContentTypeExchangeAccessor implements ExchangeAccessor {

	public static final String ID = "Response Content-Type";
	
	public Object get(Exchange exc) {
		if (exc == null || exc.getResponse() == null || exc.getResponse().getHeader() == null || exc.getResponse().getHeader().getContentType() == null)
			return "N/A";
		return exc.getResponse().getHeader().getContentType();
	}

	public String getId() {
		return ID;
	}

}
