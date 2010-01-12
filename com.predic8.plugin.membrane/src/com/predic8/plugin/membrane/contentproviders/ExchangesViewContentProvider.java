package com.predic8.plugin.membrane.contentproviders;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;

import com.predic8.membrane.core.exchange.Exchange;

public class ExchangesViewContentProvider implements IStructuredContentProvider {


	int maximumExchangeCount = Integer.MAX_VALUE;
	
	List<Integer> statusCodeFilter = new ArrayList<Integer>();
	
	public Object[] getElements(Object inputElement) {
		if (inputElement == null)
			return new Object[0];
		
		statusCodeFilter.clear();
		statusCodeFilter.add(500);
		statusCodeFilter.add(301);
		statusCodeFilter.add(302);
		statusCodeFilter.add(303);
		statusCodeFilter.add(304);
		
		Object[] original = (Object[])inputElement;
		List<Object> filtered = new ArrayList<Object>();
		
		for (Object object : original) {
			if (object instanceof Exchange) {
				if (statusCodeFilter.contains( ((Exchange)object) .getResponse().getStatusCode())) {
					filtered.add(object);
				}
			}
		}
		
		Exchange[] array = filtered.toArray(new Exchange[filtered.size()]);
		if (array.length <= maximumExchangeCount) {
			return array;
		}
		
		Object[] result = new Object[maximumExchangeCount];
		
		
		System.arraycopy(array, array.length - maximumExchangeCount - 1, result, 0, maximumExchangeCount);
		
		return result;
	}

	public void dispose() {
		
	}

	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		
	}

	public synchronized void setMaxExcahngeCount(int number) {
		if (number > 0)
			this.maximumExchangeCount = number;
	}
}
