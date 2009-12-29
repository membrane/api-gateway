package com.predic8.plugin.membrane.filtering;

import java.util.HashSet;
import java.util.Set;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class ClientFilter extends AbstractExchangesFilter {

	
	private Set<String> displayedItems = new HashSet<String>();
	
	public ClientFilter() {
		showAll = true;
	}

	public Set<String> getDisplayedClients() {
		return displayedItems;
	}

	public void setDisplayedClients(Set<String> displayedClients) {
		this.displayedItems = displayedClients;
	}

	public boolean filter(Exchange exc) {
		if (showAll)
			return true;
		
		if (displayedItems.contains((String)exc.getProperty(HttpTransport.SOURCE_HOSTNAME)))
			return true;
		
		return false;
	}

	public boolean isDeactivated() {
		if (showAll)
			return true;
		if (displayedItems.isEmpty())
			return true;
		return false;
	}

}
