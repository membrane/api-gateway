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
package com.predic8.plugin.membrane.dialogs.components;

import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;

import com.predic8.plugin.membrane.filtering.ExchangesFilter;

public class MethodSelectionAdapter extends SelectionAdapter {

	private Button bt;
	
	private ExchangesFilter filter;
	
	public MethodSelectionAdapter(Button bt, ExchangesFilter filter) {
		this.bt = bt;
		this.filter = filter;
	}
	
	@Override
	public void widgetSelected(SelectionEvent e) {
		if (bt.getSelection()) {
			filter.getDisplayedItems().add((String) bt.getData());
		} else {
			filter.getDisplayedItems().remove((String) bt.getData());
		}
	}
	
}
