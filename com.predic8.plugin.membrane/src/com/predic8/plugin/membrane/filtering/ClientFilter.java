package com.predic8.plugin.membrane.filtering;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class ClientFilter extends AbstractExchangesFilter {

	public boolean filter(Exchange exc) {
		if (showAll)
			return true;
		
		return displayedItems.contains((String)exc.getProperty(HttpTransport.SOURCE_HOSTNAME));
	}

}
