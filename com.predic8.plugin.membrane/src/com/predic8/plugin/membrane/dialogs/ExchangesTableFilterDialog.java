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

package com.predic8.plugin.membrane.dialogs;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

import com.predic8.plugin.membrane.dialogs.components.ClientFilterComposite;
import com.predic8.plugin.membrane.dialogs.components.MethodFilterComposite;
import com.predic8.plugin.membrane.dialogs.components.RuleFilterComposite;
import com.predic8.plugin.membrane.dialogs.components.ServerFilterComposite;
import com.predic8.plugin.membrane.dialogs.components.StatusCodeFilterComposite;
import com.predic8.plugin.membrane.filtering.ClientFilter;
import com.predic8.plugin.membrane.filtering.MethodFilter;
import com.predic8.plugin.membrane.filtering.RulesFilter;
import com.predic8.plugin.membrane.filtering.ServerFilter;
import com.predic8.plugin.membrane.filtering.StatusCodeFilter;
import com.predic8.plugin.membrane.views.ExchangesView;

public class ExchangesTableFilterDialog extends Dialog {

	private ExchangesView exchangesView;

	private TabFolder tabFolder;

	private MethodFilterComposite methodFilterComposite;
	
	private RuleFilterComposite rulesFilterComposite;
	
	private ServerFilterComposite serverFilterComposite;
	
	private ClientFilterComposite clientFilterComposite;
	
	private StatusCodeFilterComposite statusCodeFilterComposite;
	
	Button BtRemoveFilters;
	
	public ExchangesTableFilterDialog(Shell parentShell, ExchangesView parent) {
		super(parentShell);
		this.exchangesView = parent;

	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Exchange Filters");
		shell.setSize(440, 560);
	}

	@Override
	protected Control createButtonBar(Composite parent) {
		 Composite composite = new Composite(parent, SWT.NONE);
		 
		 GridLayout layout = new GridLayout();
		 composite.setLayout(layout);
 		 
		 BtRemoveFilters = new Button(composite, SWT.PUSH);
		 BtRemoveFilters.addSelectionListener(new SelectionAdapter() {
			 @Override
			public void widgetSelected(SelectionEvent e) {
				 rulesFilterComposite.showAllRules();
				 methodFilterComposite.showAllMethods();
				 serverFilterComposite.showAllServers();
				 clientFilterComposite.showAllClients();
				 statusCodeFilterComposite.showAllStatusCodes();
			}
		 });
		 BtRemoveFilters.setText("Remove  all  filters");
		
		 GridData gData = new GridData(SWT.RIGHT, SWT.FILL, true, true, 1, 1);
		 gData.grabExcessHorizontalSpace = true;
		 BtRemoveFilters.setLayoutData(gData);

		 Label label = new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL);
		 GridData labelGridData = new GridData(410, 12);
		 label.setLayoutData(labelGridData);
		 
		return super.createButtonBar(composite);
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);

		GridLayout layout = new GridLayout();
		layout.marginTop = 20;
		layout.marginLeft = 20;
		layout.marginBottom = 20;
		layout.marginRight = 20;
		container.setLayout(layout);

		
		RulesFilter rulesFilter = null;
		if (exchangesView.getFilterManager().getFilterForClass(RulesFilter.class) != null) {
			rulesFilter = (RulesFilter) exchangesView.getFilterManager().getFilterForClass(RulesFilter.class);
		} else {
			rulesFilter = new RulesFilter();
		}

		MethodFilter methodFilter = null;
		if (exchangesView.getFilterManager().getFilterForClass(MethodFilter.class) != null) {
			methodFilter = (MethodFilter) exchangesView.getFilterManager().getFilterForClass(MethodFilter.class);
		} else {
			
			methodFilter = new MethodFilter();
		}

		ServerFilter serverFilter = null;
		if (exchangesView.getFilterManager().getFilterForClass(ServerFilter.class) != null) {
			serverFilter = (ServerFilter) exchangesView.getFilterManager().getFilterForClass(ServerFilter.class);
		} else {
			serverFilter = new ServerFilter();
		}
		
		
		ClientFilter clientFilter = null;
		if (exchangesView.getFilterManager().getFilterForClass(ClientFilter.class) != null) {
			clientFilter = (ClientFilter) exchangesView.getFilterManager().getFilterForClass(ClientFilter.class);
		} else {
			clientFilter = new ClientFilter();
		}
		
		StatusCodeFilter statusCodeFilter = null;
		if (exchangesView.getFilterManager().getFilterForClass(StatusCodeFilter.class) != null) {
			statusCodeFilter = (StatusCodeFilter) exchangesView.getFilterManager().getFilterForClass(StatusCodeFilter.class);
		} else {
			statusCodeFilter = new StatusCodeFilter();
		}
		
		
		tabFolder = new TabFolder(container, SWT.NONE);		
		
		methodFilterComposite = new MethodFilterComposite(tabFolder, methodFilter);
		rulesFilterComposite = new RuleFilterComposite(tabFolder, rulesFilter);
		serverFilterComposite = new ServerFilterComposite(tabFolder, serverFilter);
		clientFilterComposite = new ClientFilterComposite(tabFolder, clientFilter);
		statusCodeFilterComposite = new StatusCodeFilterComposite(tabFolder, statusCodeFilter);
		
		GridData gdTabs = new GridData();
		gdTabs.grabExcessHorizontalSpace = true;
		gdTabs.widthHint = 400;
		tabFolder.setLayoutData(gdTabs);
		
		TabItem tabItemRule = new TabItem(tabFolder, SWT.NONE);
		tabItemRule.setText("Rule");
		tabItemRule.setControl(rulesFilterComposite);

		TabItem tabItemMethod = new TabItem(tabFolder, SWT.NONE);
		tabItemMethod.setText("Method");
		tabItemMethod.setControl(methodFilterComposite);

		TabItem tabItemStatusCode = new TabItem(tabFolder, SWT.NONE);
		tabItemStatusCode.setText("Status Code");
		tabItemStatusCode.setControl(statusCodeFilterComposite);
		
		TabItem tabItemServer = new TabItem(tabFolder, SWT.NONE);
		tabItemServer.setText("Server");
		tabItemServer.setControl(serverFilterComposite);
		
		TabItem tabItemClient = new TabItem(tabFolder, SWT.NONE);
		tabItemClient.setText("Client");
		tabItemClient.setControl(clientFilterComposite);
		
		return container;
	}

	@Override
	protected void initializeBounds() {
		super.initializeBounds();
		Shell shell = this.getShell();
		Monitor primary = shell.getMonitor();
		Rectangle bounds = primary.getBounds();
		Rectangle rect = shell.getBounds();
		int x = bounds.x + (bounds.width - rect.width) / 2;
		int y = bounds.y + (bounds.height - rect.height) / 2;
		shell.setLocation(x, y);
	}

	@Override
	protected void okPressed() {
		if (BtRemoveFilters.getSelection()) {
			exchangesView.getFilterManager().removeAllFilters();
			exchangesView.reloadAll();
			return;
		}
		if (rulesFilterComposite.getRulesFilter().isDeactivated()) {
			exchangesView.getFilterManager().removeFilter(RulesFilter.class);
		} else {
			exchangesView.getFilterManager().addFilter(rulesFilterComposite.getRulesFilter());
		}
		
		if (methodFilterComposite.getMethodFilter().isDeactivated()) {
			exchangesView.getFilterManager().removeFilter(MethodFilter.class);
		} else {
			exchangesView.getFilterManager().addFilter(methodFilterComposite.getMethodFilter());
		}
		
		if (serverFilterComposite.getServerFilter().isDeactivated()) {
			exchangesView.getFilterManager().removeFilter(ServerFilter.class);
		} else {
			exchangesView.getFilterManager().addFilter(serverFilterComposite.getServerFilter());
		}
		
		if (clientFilterComposite.getClientFilter().isDeactivated()) {
			exchangesView.getFilterManager().removeFilter(ClientFilter.class);
		} else {
			exchangesView.getFilterManager().addFilter(clientFilterComposite.getClientFilter());
		}
		
		if (statusCodeFilterComposite.getStatusCodeFilter().isDeactivated()) {
			exchangesView.getFilterManager().removeFilter(StatusCodeFilter.class);
		} else {
			exchangesView.getFilterManager().addFilter(statusCodeFilterComposite.getStatusCodeFilter());
		}
		
		exchangesView.reloadAll();
		super.okPressed();
	}

}