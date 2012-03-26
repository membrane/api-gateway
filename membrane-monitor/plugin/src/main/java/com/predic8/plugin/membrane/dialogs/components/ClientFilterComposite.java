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
import com.predic8.plugin.membrane.filtering.ClientFilter;
import com.predic8.plugin.membrane.filtering.ExchangesFilter;

public class ClientFilterComposite extends AbstractFilterComposite {

	public ClientFilterComposite(Composite parent, ExchangesFilter aFilter) {
		super(parent, aFilter);
		
	}
	
	
	protected void initializeButtons(Composite rulesComposite) {
		Object[] excanges = Router.getInstance().getExchangeStore().getAllExchanges();
		Set<String> clients = new HashSet<String>();
		if (excanges != null && excanges.length > 0) {
			for (Object object : excanges) {
				try {
					AbstractExchange exc = (AbstractExchange) object;
					clients.add(exc.getSourceHostname());
				} catch (Exception e) {
					e.printStackTrace();
				}

			}
		}

		for (String client : clients) {
			final Button bt = new Button(rulesComposite, SWT.CHECK);
			bt.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
			bt.setText(client);
			bt.setData(client);
			if (((ClientFilter) filter).getDisplayedItems().contains(client)) {
				bt.setSelection(true);
			}

			bt.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					if (bt.getSelection()) {
						((ClientFilter) filter).getDisplayedItems().add((String) bt.getData());
					} else {
						((ClientFilter) filter).getDisplayedItems().remove((String) bt.getData());
					}
				}
			});
			buttons.add(bt);
		}
	}


	@Override
	protected String getGroupText() {
		return "Show Clients";
	}

	@Override
	protected String getShowAllText() {
		return "Display exchanges from all clients";
	}

	@Override
	protected String getShowSelectedOnlyText() {
		return "Display exchanges from selected clients only";
	}
	
	@Override
	public String getFilterName() {
		return "Client";
	}

}
