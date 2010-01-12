package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.StructuredViewer;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.plugin.membrane.contentproviders.ExchangesViewLazyContentProvider;

public class ExchangeVirtualListRemoveAction extends Action {

	
	private StructuredViewer structuredViewer;
	
	
	public ExchangeVirtualListRemoveAction(StructuredViewer structuredViewer) {
		super();
		this.structuredViewer = structuredViewer;
		setText("Remove all visible exchanges");
		setId("remove all visible exhanges action");
	}
	
	public void run() {
		try {
			
			ExchangesViewLazyContentProvider contentViewer = (ExchangesViewLazyContentProvider)structuredViewer.getContentProvider();
			Object[] objects = contentViewer.getExchanges();
			if (objects == null || objects.length == 0)
				return;
			
			Exchange[] array = new Exchange[objects.length];
			for(int i = 0; i < array.length; i ++ ) {
				array[i] = (Exchange)objects[i];
			}
			
			Router.getInstance().getExchangeStore().removeAllExchanges(array);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	} 
	
	
	
}
