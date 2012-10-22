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