package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class ClientExchangeAccessor implements ExchangeAccessor {

	public static final String ID = "Client";
	
	public String get(Exchange exc) {
		if (exc == null || exc.getProperty(HttpTransport.SOURCE_HOSTNAME) == null)
			return "";
		return (String)exc.getProperty(HttpTransport.SOURCE_HOSTNAME);
	}

	public String getId() {
		return ID;
	}

}
