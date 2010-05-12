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
package com.predic8.plugin.membrane.dialogs.rule.composites;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;

import com.predic8.membrane.core.Router;
import com.predic8.plugin.membrane.actions.ShowSecurityPreferencesAction;
import com.predic8.plugin.membrane.components.RuleKeyGroup;

public class ForwardingRuleKeyTabComposite extends Composite {

	private RuleKeyGroup ruleKeyGroup;
	
	private Button btSecureConnection;
	
	public ForwardingRuleKeyTabComposite(Composite parent) {
		super(parent, SWT.NONE);
		setGridLayout();
	
		createSecurityComposite(this);
		
		ruleKeyGroup = new RuleKeyGroup(this, SWT.NONE);
		
	}

	private void createSecureConnectionButton(Composite parent) {
		btSecureConnection = new Button(parent, SWT.CHECK);
		btSecureConnection.setText("Secure Connection (SSL/STL)");
		btSecureConnection.setEnabled(Router.getInstance().getConfigurationManager().getConfiguration().isSecurityConfigurationAvailable());
	}

	private void setGridLayout() {
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		gridLayout.marginTop = 12;
		gridLayout.marginLeft = 12;
		gridLayout.marginBottom = 12;
		gridLayout.marginRight = 12;
		setLayout(gridLayout);
	}

	private void createSecurityComposite(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginBottom = 10;
		composite.setLayout(layout);
		
		createSecureConnectionButton(composite);
		
		Label label = new Label(composite, SWT.NONE);
		label.setText("To enable secure connection you must provide keystore and truststore data.");
	
		createLink(composite, "<A>Security Preferences Page</A>");
	}
	 
	private void createLink(Composite composite, String linkText) {
		Link link = new Link(composite, SWT.NONE);
		link.setText(linkText);
		link.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ShowSecurityPreferencesAction action = new ShowSecurityPreferencesAction();
				action.run();
			}
		});
	}
	
	public boolean getSecureConnection() {
		return btSecureConnection.getSelection();
	}
	
	public void setSecureConnection(boolean selected) {
		btSecureConnection.setSelection(selected);
	}
	
	public RuleKeyGroup getRuleKeyGroup() {
		return ruleKeyGroup;
	}
	
}
