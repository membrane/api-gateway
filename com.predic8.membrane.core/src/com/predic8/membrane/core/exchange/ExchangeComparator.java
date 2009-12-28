package com.predic8.membrane.core.exchange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.predic8.membrane.core.exchange.accessors.ExchangeAccessor;

public class ExchangeComparator implements Comparator<Exchange> {

	private List<ExchangeAccessor> accessors = new ArrayList<ExchangeAccessor>();
	
	private boolean ascending = true;
	
	public int compare(Exchange e1, Exchange e2) {
		if (e1.getResponse() == null || e2.getResponse() == null)
			return 0;
		
		for (ExchangeAccessor accessor : accessors) {
			
			Comparable comp1 = (Comparable)accessor.get(e1);
			Comparable comp2 = (Comparable)accessor.get(e2);
			
			int result = comp1.compareTo(comp2);
			if (result != 0) {
				if (ascending) {
					return comp1.compareTo(comp2);
				} else {
					return comp2.compareTo(comp1);
				}
			}
		}
		
		return 0;
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

	public boolean isAscending() {
		return ascending;
	}

	public void setAscending(boolean ascending) {
		this.ascending = ascending;
	}
	
	@Override
	public String toString() {
		if (isEmpty())
			return "NONE";
		if (accessors.size() == 1)
			return accessors.get(0).getId();
		StringBuffer buffer = new StringBuffer();
		
		for (int i = 0; i < accessors.size() - 1; i++) {
			buffer.append(accessors.get(i).getId() + " and ");
		}
		
		buffer.append(accessors.get(accessors.size() - 1).getId());
		
		return buffer.toString();
	}
}
