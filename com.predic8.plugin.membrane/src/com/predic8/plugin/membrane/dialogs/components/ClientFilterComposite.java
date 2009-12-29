package com.predic8.plugin.membrane.dialogs.components;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.plugin.membrane.filtering.ClientFilter;

public class ClientFilterComposite extends Composite {

	private List<Button> buttons = new ArrayList<Button>();
	
	private ClientFilter clientFilter;
	
	private Button btShowAllClients;

	private Button btShowSelectedClientsOnly;

	
	public ClientFilterComposite(Composite parent, ClientFilter filter) {
		super(parent, SWT.NONE);
		clientFilter = filter;
		
		GridLayout layout = new GridLayout();
		layout.marginTop = 20;
		layout.marginLeft = 20;
		layout.marginBottom = 20;
		layout.marginRight = 20;
		setLayout(layout);

		Group rulesGroup = new Group(this, SWT.NONE);
		rulesGroup.setText("Show Clients");
		rulesGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));

		GridLayout gridLayout4RuleGroup = new GridLayout();
		gridLayout4RuleGroup.marginTop = 10;
		gridLayout4RuleGroup.marginLeft = 10;
		gridLayout4RuleGroup.marginRight = 10;
		rulesGroup.setLayout(gridLayout4RuleGroup);

		btShowAllClients = new Button(rulesGroup, SWT.RADIO);
		btShowAllClients.setText("Display exchanges from all clients");
		btShowAllClients.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {

				if (btShowAllClients.getSelection()) {
					btShowSelectedClientsOnly.setSelection(false);
					for (Button button : buttons) {
						button.setEnabled(false);
						clientFilter.setShowAll(true);
					}
				}
			}
		});

		btShowSelectedClientsOnly = new Button(rulesGroup, SWT.RADIO);
		btShowSelectedClientsOnly.setText("Display exchanges from selected clients only");
		btShowSelectedClientsOnly.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (btShowSelectedClientsOnly.getSelection()) {
					Set<String> toDisplay = clientFilter.getDisplayedClients();
					for (Button button : buttons) {
						button.setEnabled(true);
						if (toDisplay.contains(button.getData())) {
							button.setSelection(true);
						} else {
							button.setSelection(false);
						}
					}
					clientFilter.setShowAll(false);
				}
			}
		});

		Composite rulesComposite = new Composite(rulesGroup, SWT.BORDER);
		rulesComposite.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		GridData rulesGridData = new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL);
		rulesComposite.setLayoutData(rulesGridData);

		GridLayout rulesLayout = new GridLayout();
		rulesComposite.setLayout(rulesLayout);

		Object[] excanges = Router.getInstance().getExchangeStore().getAllExchanges();
		Set<String> clients = new HashSet<String>();
		if (excanges != null && excanges.length > 0) {
			for (Object object : excanges) {
				try {
					Exchange exc = (Exchange)object;
					clients.add(((String)(exc.getProperty(HttpTransport.SOURCE_HOSTNAME))));
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
			if (clientFilter.getDisplayedClients().contains(client)) {
				bt.setSelection(true);
			}

			bt.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					if (bt.getSelection()) {
						clientFilter.getDisplayedClients().add((String) bt.getData());
					} else {
						clientFilter.getDisplayedClients().remove((String) bt.getData());
					}
				}
			});
			buttons.add(bt);
		}
		

		if (clientFilter.isShowAll()) {
			btShowAllClients.setSelection(true);
			btShowAllClients.notifyListeners(SWT.Selection, null);
		} else {
			btShowSelectedClientsOnly.setSelection(true);
			btShowSelectedClientsOnly.notifyListeners(SWT.Selection, null);
		}

	}


	public ClientFilter getClientFilter() {
		return clientFilter;
	}


	public void showAllClients() {
		btShowAllClients.setSelection(true);
		btShowAllClients.notifyListeners(SWT.Selection, null);
	}


}
