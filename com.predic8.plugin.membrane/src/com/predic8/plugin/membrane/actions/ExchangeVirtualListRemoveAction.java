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
