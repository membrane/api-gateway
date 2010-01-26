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

import java.util.HashSet;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.plugin.membrane.filtering.ExchangesFilter;

public class StatusCodeFilterComposite extends AbstractFilterComposite {

	
	public StatusCodeFilterComposite(Composite parent, ExchangesFilter aFilter) {
		super(parent, aFilter);
	}


	@Override
	protected String getGroupText() {
		return "Show Servers";
	}


	@Override
	protected String getShowAllText() {
		return "Display exchanges with any status code";
	}


	@Override
	protected String getShowSelectedOnlyText() {
		return "Display exchanges with selected status codes only";
	}


	@Override
	protected void initializeButtons(Composite composite) {
		Object[] excanges = Router.getInstance().getExchangeStore().getAllExchanges();
		Set<Integer> statusCodes = new HashSet<Integer>();
		if (excanges != null && excanges.length > 0) {
			for (Object object : excanges) {
				try {
					Exchange exc = (Exchange)object;
					if (exc.getResponse() == null)
						continue;
					statusCodes.add(exc.getResponse().getStatusCode());
				} catch (Exception e) {
					e.printStackTrace();
				}
				
			}
		}
		
		
		for (Integer statusCode : statusCodes) {
			final Button bt = new Button(composite, SWT.CHECK);
			bt.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
			bt.setText(Integer.toString(statusCode));
			bt.setData(statusCode);
			
			bt.setSelection(filter.getDisplayedItems().contains(statusCode));
			
			bt.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					if (bt.getSelection()) {
						filter.getDisplayedItems().add((Integer) bt.getData());
					} else {
						filter.getDisplayedItems().remove((Integer) bt.getData());
					}
				}
			});
			buttons.add(bt);
		}
		
	}

	
	@Override
	public String getFilterName() {
		return "Status Code";
	}

}
