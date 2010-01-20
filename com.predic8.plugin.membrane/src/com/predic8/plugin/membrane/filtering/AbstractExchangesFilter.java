package com.predic8.plugin.membrane.filtering;

import java.util.HashSet;
import java.util.Set;


public abstract class AbstractExchangesFilter implements ExchangesFilter {

	protected boolean showAll = true;
	
	protected Set<Object> displayedItems = new HashSet<Object>();
	
	public boolean isShowAll() {
		return showAll;
	}

	public void setShowAll(boolean showAll) {
		this.showAll = showAll;
	}

	public Set<Object> getDisplayedItems() {
		return displayedItems;
	}

	public void setDisplayedItems(Set<Object> displayedItems) {
		this.displayedItems = displayedItems;
	}
	
	public boolean isDeactivated() {
		if (showAll)
			return true;
		return displayedItems.isEmpty();
	}

}
