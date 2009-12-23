package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;

public class RuleExchangeAccessor implements ExchangeAccessor {

	public static final String ID = "Rule";
	
	public Object get(Exchange exc) {
		if (exc == null)
			return "";
		return exc.getRule().toString();
	}

	public String getId() {
		return ID;
	}

}
