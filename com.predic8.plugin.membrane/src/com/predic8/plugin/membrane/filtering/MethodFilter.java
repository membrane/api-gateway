package com.predic8.plugin.membrane.filtering;

import java.util.HashSet;
import java.util.Set;

import com.predic8.membrane.core.exchange.Exchange;

public class MethodFilter implements ExchangesFilter {

	
	private boolean showAllMethods;
	
	private Set<String> displayedMethods = new HashSet<String>();

	public MethodFilter() {
		showAllMethods = true;
	}
	
	
	public boolean filter(Exchange exc) {
		if (showAllMethods)
			return true;
		
		if (displayedMethods.contains(exc.getRequest().getMethod()))
			return true;
		
		
		return false;
	}

	public boolean isShowAllMethods() {
		return showAllMethods;
	}

	public void setShowAllMethods(boolean showAllMethods) {
		this.showAllMethods = showAllMethods;
	}

	public Set<String> getDisplayedMethods() {
		return displayedMethods;
	}

	public void setDisplayedMethods(Set<String> displayedMethods) {
		this.displayedMethods = displayedMethods;
	}

}
