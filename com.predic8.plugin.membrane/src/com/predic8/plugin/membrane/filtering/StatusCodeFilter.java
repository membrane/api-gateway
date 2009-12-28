package com.predic8.plugin.membrane.filtering;

import java.util.HashSet;
import java.util.Set;

import com.predic8.membrane.core.exchange.Exchange;

public class StatusCodeFilter implements ExchangesFilter {

	
	private boolean showAllStatusCodes;
	
	private Set<Integer> displayedStatusCodes = new HashSet<Integer>();
	
	public StatusCodeFilter() {
		showAllStatusCodes = true;
	}

	public boolean isShowAllStatusCodes() {
		return showAllStatusCodes;
	}

	public void setShowAllStatusCodes(boolean showAllServers) {
		this.showAllStatusCodes = showAllServers;
	}

	public Set<Integer> getDisplayedStatusCodes() {
		return displayedStatusCodes;
	}

	public void setDisplayedStatusCodes(Set<Integer> displayedStatusCodes) {
		this.displayedStatusCodes = displayedStatusCodes;
	}

	public boolean filter(Exchange exc) {
		if (showAllStatusCodes)
			return true;
		
		if (exc.getResponse() == null)
			return false;
		
		if (displayedStatusCodes.contains(exc.getResponse().getStatusCode()))
			return true;
		
		return false;
	}

	public boolean isDeactivated() {
		if (showAllStatusCodes)
			return true;
		if (displayedStatusCodes.isEmpty())
			return true;
		return false;
	}

}
