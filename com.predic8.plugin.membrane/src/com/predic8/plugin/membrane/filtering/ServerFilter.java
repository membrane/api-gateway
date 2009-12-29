package com.predic8.plugin.membrane.filtering;

import java.util.HashSet;
import java.util.Set;

import com.predic8.membrane.core.exchange.Exchange;

public class ServerFilter extends AbstractExchangesFilter {

	
	private Set<String> displayedServers = new HashSet<String>();
	
	public ServerFilter() {
		showAll = true;
	}

	public Set<String> getDisplayedServers() {
		return displayedServers;
	}

	public void setDisplayedServers(Set<String> displayedServers) {
		this.displayedServers = displayedServers;
	}

	public boolean filter(Exchange exc) {
		if (showAll)
			return true;
		if (displayedServers.contains(exc.getRequest().getHeader().getHost()))
			return true;
		
		return false;
	}

	public boolean isDeactivated() {
		if (showAll)
			return true;
		if (displayedServers.isEmpty())
			return true;
		return false;
	}

}
