package com.predic8.plugin.membrane.filtering;

import java.util.HashSet;
import java.util.Set;

import com.predic8.membrane.core.exchange.Exchange;

public class MethodFilter extends AbstractExchangesFilter {

	private Set<String> displayedItems = new HashSet<String>();

	public MethodFilter() {
		showAll = true;
	}
	
	
	public boolean filter(Exchange exc) {
		if (showAll)
			return true;
		
		if (displayedItems.contains(exc.getRequest().getMethod()))
			return true;
		
		
		return false;
	}

	public Set<String> getDisplayedMethods() {
		return displayedItems;
	}

	public void setDisplayedMethods(Set<String> displayedMethods) {
		this.displayedItems = displayedMethods;
	}


	public boolean isDeactivated() {
		if (showAll)
			return true;
		if (displayedItems.isEmpty())
			return true;
		return false;
	}

}
