/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.statistics;

import java.util.Comparator;

public class PropertyComparator<E, T extends Comparable<T>> implements Comparator<E> {
	
	public interface ValueResolver<E, T> {
		public T get(E exc);
	}
	
	private final ValueResolver<E, T> vResolver;
	private final int order;
			
	public PropertyComparator(String order, ValueResolver<E, T> vResolver) {
		this.vResolver = vResolver;
		this.order = "desc".equals(order)?-1:1;
	}
	
	@Override
	public int compare(E o1, E o2) {
		if (vResolver.get(o1) == null && vResolver.get(o2) == null) return 0;
		if (vResolver.get(o1) == null) return -1*order;
		if (vResolver.get(o2) == null) return 1*order;
		
		return vResolver.get(o1).compareTo(vResolver.get(o2))*order;
	}
}