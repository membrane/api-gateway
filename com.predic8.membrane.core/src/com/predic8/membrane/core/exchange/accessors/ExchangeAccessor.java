package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;

public interface ExchangeAccessor {
	
	public Object get(Exchange exc);

	public String getId();
}
