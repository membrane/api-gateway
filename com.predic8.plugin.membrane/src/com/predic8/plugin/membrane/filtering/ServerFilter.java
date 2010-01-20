package com.predic8.plugin.membrane.filtering;

import com.predic8.membrane.core.exchange.Exchange;

public class ServerFilter extends AbstractExchangesFilter {
	
	public boolean filter(Exchange exc) {
		if (showAll)
			return true;
		return displayedItems.contains(exc.getRequest().getHeader().getHost());
	}


}
