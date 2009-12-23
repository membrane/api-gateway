package com.predic8.plugin.membrane.filtering;

import com.predic8.membrane.core.exchange.Exchange;

public interface ExchangesFilter {
	
	public boolean filter(Exchange exc); 
	
	public boolean isDeactivated();
	
}
