package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;

public interface ExchangeAccessor {
	
	public String get(Exchange exc);

	public String getId();
}
