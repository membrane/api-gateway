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
package com.predic8.plugin.membrane.providers;

import org.eclipse.jface.viewers.ILazyContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;

public class ExchangesViewLazyContentProvider implements ILazyContentProvider {

	private Object[] exchanges;
	private TableViewer viewer;
	
	public ExchangesViewLazyContentProvider(TableViewer viewer) {
		this.viewer = viewer;
	}
	
	public void updateElement(int index) {
		viewer.replace(exchanges[index], index);
	}

	public void dispose() {

	}

	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		this.exchanges = (Object[]) newInput;
	}

	public Object[] getExchanges() {
		return exchanges;
	}	

}
