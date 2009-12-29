package com.predic8.plugin.membrane.filtering;

import java.util.HashSet;
import java.util.Set;

import com.predic8.membrane.core.exchange.Exchange;

public class StatusCodeFilter extends AbstractExchangesFilter {

	
	private Set<Integer> displayedStatusCodes = new HashSet<Integer>();
	
	public StatusCodeFilter() {
		showAll = true;
	}

	public Set<Integer> getDisplayedStatusCodes() {
		return displayedStatusCodes;
	}

	public void setDisplayedStatusCodes(Set<Integer> displayedStatusCodes) {
		this.displayedStatusCodes = displayedStatusCodes;
	}

	public boolean filter(Exchange exc) {
		if (showAll)
			return true;
		
		if (exc.getResponse() == null)
			return false;
		
		if (displayedStatusCodes.contains(exc.getResponse().getStatusCode()))
			return true;
		
		return false;
	}

	public boolean isDeactivated() {
		if (showAll)
			return true;
		if (displayedStatusCodes.isEmpty())
			return true;
		return false;
	}

}
