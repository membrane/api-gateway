package com.predic8.membrane.core.exchange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.predic8.membrane.core.exchange.accessors.ExchangeAccessor;

public class ExchangeComparator implements Comparator<Exchange> {

	private List<ExchangeAccessor> accessors = new ArrayList<ExchangeAccessor>();
	
	public int compare(Exchange o1, Exchange o2) {
		if (o1.getResponse() == null)
			return 0;
		
		StringBuffer b1 = new StringBuffer();
		StringBuffer b2 = new StringBuffer();
		for (ExchangeAccessor accessor : accessors) {
			b1.append(accessor.get(o1));
			b2.append(accessor.get(o2));
		}
		
		return b1.toString().compareTo(b2.toString()) ;
	}
	
	public void addAccessor(ExchangeAccessor acc) {
		if (acc == null)
			return;
		accessors.add(acc);
	}
	
	public void removeAccessor(ExchangeAccessor acc) {
		if (acc == null)
			return;
		accessors.remove(acc);
	}

	public void removeAllAccessors() {
		accessors.clear();
	}
	
	public boolean isEmpty() {
		return accessors.size() == 0;
	}

	public List<ExchangeAccessor> getAccessors() {
		return accessors;
	}

	public void setAccessors(List<ExchangeAccessor> accessors) {
		this.accessors = accessors;
	}
	
	
	
}
