package com.predic8.plugin.membrane.filtering;

import java.util.Set;

import com.predic8.membrane.core.exchange.Exchange;

public interface ExchangesFilter {
	
	public boolean filter(Exchange exc); 
	
	public boolean isDeactivated();
	
	public boolean isShowAll();
	
	public void setShowAll(boolean showAll);
	
	public Set<Object> getDisplayedItems();
}
