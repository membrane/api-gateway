/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.plugin.membrane.contentproviders;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;

import com.predic8.membrane.core.exchange.AbstractExchange;

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
			if (object instanceof AbstractExchange) {
				if (statusCodeFilter.contains( ((AbstractExchange)object) .getResponse().getStatusCode())) {
					filtered.add(object);
				}
			}
		}
		
		AbstractExchange[] array = filtered.toArray(new AbstractExchange[filtered.size()]);
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
