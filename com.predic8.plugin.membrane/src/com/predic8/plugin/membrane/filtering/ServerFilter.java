package com.predic8.plugin.membrane.filtering;

import java.util.HashSet;
import java.util.Set;

import com.predic8.membrane.core.exchange.Exchange;

public class ServerFilter implements ExchangesFilter {

	
	private boolean showAllServers;
	
	private Set<String> displayedServers = new HashSet<String>();
	
	public ServerFilter() {
		showAllServers = true;
	}

	public boolean isShowAllServers() {
		return showAllServers;
	}

	public void setShowAllServers(boolean showAllServers) {
		this.showAllServers = showAllServers;
	}

	public Set<String> getDisplayedServers() {
		return displayedServers;
	}

	public void setDisplayedServers(Set<String> displayedServers) {
		this.displayedServers = displayedServers;
	}

	public boolean filter(Exchange exc) {
		if (showAllServers)
			return true;
		if (displayedServers.contains(exc.getRequest().getHeader().getHost()))
			return true;
		
		return false;
	}

	public boolean isDeactivated() {
		if (showAllServers)
			return true;
		if (displayedServers.isEmpty())
			return true;
		return false;
	}

}
