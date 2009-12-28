package com.predic8.plugin.membrane.filtering;

import java.util.HashMap;
import java.util.Map;

import com.predic8.membrane.core.exchange.Exchange;

public class FilterManager {

	private Map<Class<? extends ExchangesFilter>, ExchangesFilter> filters = new HashMap<Class<? extends ExchangesFilter>, ExchangesFilter>();
	
	public ExchangesFilter getFilterForClass(Class<? extends ExchangesFilter> clazz) {
		return filters.get(clazz);
	}
	
	public void addFilter(ExchangesFilter filter) {
		filters.put(filter.getClass(), filter);
	}
	
	public void removeFilter(Class<? extends ExchangesFilter> clazz) {
		filters.remove(clazz);
	}
	
	
	
	public boolean isEmpty() {
		return filters.size() == 0;
	}
	
	
	public boolean filter(Exchange exc) {
		for (ExchangesFilter filter : filters.values()) {
			if (!filter.filter(exc)) {
				return false;
			}
		} 
		return true;
	}
	
	
	@Override
	public String toString() {
		if (isEmpty())
			return "are deactivated:   ";
		return "are activated:   ";
	}

	public void removeAllFilters() {
		filters.clear();
	}
}
