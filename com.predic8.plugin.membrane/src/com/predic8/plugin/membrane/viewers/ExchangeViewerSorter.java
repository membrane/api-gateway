package com.predic8.plugin.membrane.viewers;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;

import com.predic8.membrane.core.exchange.HttpExchange;

public class ExchangeViewerSorter extends ViewerSorter {

	public static final int SORT_TARGET_TIME = 0;
	
	private int sortTarget = -1;  

		
	@Override
	public int compare(Viewer viewer, Object e1, Object e2) {
		
		if (sortTarget < 0)
			return super.compare(viewer, e1, e2);
		
		try {
			
			HttpExchange obj1 = (HttpExchange)e1;
			HttpExchange obj2 = (HttpExchange)e2;
			
			switch (sortTarget) {
			case SORT_TARGET_TIME:
				return obj1.getTime().compareTo(obj2.getTime());
				
				default:
					return super.compare(viewer, e1, e2);
				
			}
			
		} catch (Exception e) {
			System.out.println("sorting failed due to exception.");
		}
		return super.compare(viewer, e1, e2);
	}

	public int getSortTarget() {
		return sortTarget;
	}

	public void setSortTarget(int sortTarget) {
		this.sortTarget = sortTarget;
	}
	
	
	
	
}
