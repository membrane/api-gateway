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
import com.predic8.plugin.membrane.filtering.ServerFilter;

public class ServerFilterComposite extends Composite {

	private List<Button> buttons = new ArrayList<Button>();
	
	private ServerFilter serverFilter;
	
	private Button btShowAllServers;

	private Button btShowSelectedServersOnly;

	
	public ServerFilterComposite(Composite parent, ServerFilter filter) {
		super(parent, SWT.NONE);
		serverFilter = filter;
		
		GridLayout layout = new GridLayout();
		layout.marginTop = 20;
		layout.marginLeft = 20;
		layout.marginBottom = 20;
		layout.marginRight = 20;
		setLayout(layout);

		Group rulesGroup = new Group(this, SWT.NONE);
		rulesGroup.setText("Show Servers");
		rulesGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));

		GridLayout gridLayout4RuleGroup = new GridLayout();
		gridLayout4RuleGroup.marginTop = 10;
		gridLayout4RuleGroup.marginLeft = 10;
		gridLayout4RuleGroup.marginRight = 10;
		rulesGroup.setLayout(gridLayout4RuleGroup);

		btShowAllServers = new Button(rulesGroup, SWT.RADIO);
		btShowAllServers.setText("Display exchanges from all servers");
		btShowAllServers.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {

				if (btShowAllServers.getSelection()) {
					btShowSelectedServersOnly.setSelection(false);
					for (Button button : buttons) {
						button.setEnabled(false);
						serverFilter.setShowAll(true);
					}
				}
			}
		});

		btShowSelectedServersOnly = new Button(rulesGroup, SWT.RADIO);
		btShowSelectedServersOnly.setText("Display exchanges from selected servers only");
		btShowSelectedServersOnly.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (btShowSelectedServersOnly.getSelection()) {
					Set<String> toDisplay = serverFilter.getDisplayedServers();
					for (Button button : buttons) {
						button.setEnabled(true);
						if (toDisplay.contains(button.getData())) {
							button.setSelection(true);
						} else {
							button.setSelection(false);
						}
					}
					serverFilter.setShowAll(false);
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
		Set<String> servers = new HashSet<String>();
		if (excanges != null && excanges.length > 0) {
			for (Object object : excanges) {
				try {
					servers.add(((Exchange)object).getRequest().getHeader().getHost());
				} catch (Exception e) {
					e.printStackTrace();
				}
				
			}
		}
		
		
		for (String server : servers) {
			final Button bt = new Button(rulesComposite, SWT.CHECK);
			bt.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
			bt.setText(server);
			bt.setData(server);
			if (serverFilter.getDisplayedServers().contains(server)) {
				bt.setSelection(true);
			}

			bt.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					if (bt.getSelection()) {
						serverFilter.getDisplayedServers().add((String) bt.getData());
					} else {
						serverFilter.getDisplayedServers().remove((String) bt.getData());
					}
				}
			});
			buttons.add(bt);
		}

		if (serverFilter.isShowAll()) {
			btShowAllServers.setSelection(true);
			btShowAllServers.notifyListeners(SWT.Selection, null);
		} else {
			btShowSelectedServersOnly.setSelection(true);
			btShowSelectedServersOnly.notifyListeners(SWT.Selection, null);
		}

	}


	public ServerFilter getServerFilter() {
		return serverFilter;
	}


	public void showAllServers() {
		btShowAllServers.setSelection(true);
		btShowAllServers.notifyListeners(SWT.Selection, null);
	}


}
