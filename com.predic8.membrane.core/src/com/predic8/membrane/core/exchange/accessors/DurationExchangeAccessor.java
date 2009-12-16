package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;

public class DurationExchangeAccessor implements ExchangeAccessor {

	public static final String ID = "Duration";
	
	public String get(Exchange exc) {
		if (exc == null)
			return "";
		return "" + (exc.getTimeResReceived() - exc.getTimeReqSent());
	}

	public String getId() {
		return ID;
	}

}
