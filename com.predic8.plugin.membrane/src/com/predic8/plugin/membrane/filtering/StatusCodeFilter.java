package com.predic8.plugin.membrane.filtering;

import com.predic8.membrane.core.exchange.Exchange;

public class StatusCodeFilter extends AbstractExchangesFilter {

	public boolean filter(Exchange exc) {
		if (showAll)
			return true;
		
		if (exc.getResponse() == null)
			return false;
		
		return displayedItems.contains(exc.getResponse().getStatusCode());
	}
}
