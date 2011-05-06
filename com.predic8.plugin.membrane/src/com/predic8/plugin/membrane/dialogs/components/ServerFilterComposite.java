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
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.plugin.membrane.filtering.ExchangesFilter;

public class ServerFilterComposite extends AbstractFilterComposite {

	
	public ServerFilterComposite(Composite parent, ExchangesFilter aFilter) {
		super(parent, aFilter);
	}


	@Override
	protected String getGroupText() {
		return "Show Servers";
	}


	@Override
	protected String getShowAllText() {
		return "Display exchanges from all servers";
	}


	@Override
	protected String getShowSelectedOnlyText() {
		return "Display exchanges from selected servers only";
	}


	@Override
	protected void initializeButtons(Composite composite) {
		Object[] excanges = Router.getInstance().getExchangeStore().getAllExchanges();
		Set<String> servers = new HashSet<String>();
		if (excanges != null && excanges.length > 0) {
			for (Object object : excanges) {
				try {
					servers.add(((AbstractExchange)object).getRequest().getHeader().getHost());
				} catch (Exception e) {
					e.printStackTrace();
				}
				
			}
		}
		
		for (String server : servers) {
			final Button bt = new Button(composite, SWT.CHECK);
			bt.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
			bt.setText(server);
			bt.setData(server);
			bt.setSelection(filter.getDisplayedItems().contains(server));
			

			bt.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					if (bt.getSelection()) {
						filter.getDisplayedItems().add((String) bt.getData());
					} else {
						filter.getDisplayedItems().remove((String) bt.getData());
					}
				}
			});
			buttons.add(bt);
		}

	}


	@Override
	public String getFilterName() {
		return "Server";
	}


}
