package com.predic8.plugin.membrane.filtering;

import java.util.HashSet;
import java.util.Set;

import com.predic8.membrane.core.exchange.Exchange;

public class ClientFilter implements ExchangesFilter {

	
	private boolean showAllClients;
	
	private Set<String> displayedClients = new HashSet<String>();
	
	public ClientFilter() {
		showAllClients = true;
	}

	public boolean isShowAllClients() {
		return showAllClients;
	}

	public void setShowAllClients(boolean showAllClients) {
		this.showAllClients = showAllClients;
	}

	public Set<String> getDisplayedClients() {
		return displayedClients;
	}

	public void setDisplayedClients(Set<String> displayedClients) {
		this.displayedClients = displayedClients;
	}

	public boolean filter(Exchange exc) {
		if (showAllClients)
			return true;
		//TODO
		if (displayedClients.contains(exc.getRule().getRuleKey()))
			return true;
		
		return false;
	}

	public boolean isDeactivated() {
		if (showAllClients)
			return true;
		if (displayedClients.isEmpty())
			return true;
		return false;
	}

}
