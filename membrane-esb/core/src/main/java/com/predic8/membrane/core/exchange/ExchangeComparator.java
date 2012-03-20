/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exchange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.predic8.membrane.core.exchange.accessors.ExchangeAccessor;

public class ExchangeComparator implements Comparator<AbstractExchange> {

	private List<ExchangeAccessor> accessors = new ArrayList<ExchangeAccessor>();
	
	private boolean ascending = true;
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public int compare(AbstractExchange e1, AbstractExchange e2) {
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
	
	public void addAccessors(ExchangeAccessor[] excAccessors) {
		if (excAccessors == null || excAccessors.length == 0) 
			return;
		for (ExchangeAccessor accessor : excAccessors) {
			if (accessor!= null)
				accessors.add(accessor);
		}
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
