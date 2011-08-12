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

package com.predic8.plugin.membrane.wizards;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

public class ProxyTypeSelectionPage extends AbstractProxyWizardPage {

	public static final String PAGE_NAME = "Type Selection";
	
	private Button btSimpleProxy;
	
	private Button btAdvancedProxy;
	
	protected Button btProxyRule;
	
	protected ProxyTypeSelectionPage() {
		super(PAGE_NAME);
		setTitle(" Add new Proxy for a ");
	}

	public void createControl(Composite parent) {
		Composite composite = createComposite(parent, 2);
		
		btSimpleProxy = createRuleButton(composite, "Simple Service Proxy");
		createFullDescriptionLabel(composite, "Create service proxy that forwards  HTTP and SOAP over HTTP requests.");
		addVericalGap(composite);
		
		btAdvancedProxy = createRuleButton(composite, "Advanced Service Proxy");
		createFullDescriptionLabel(composite, "Offers all available options for service proxies like virtual host,\nHTTP method and request URL.");
		addVericalGap(composite);
		
		btProxyRule = createRuleButton(composite, "HTTP  Proxy");
		createFullDescriptionLabel(composite, "Works like a regular HTTP Proxy.\nCan proxy SOAP and HTTP requests.");
		setControl(composite);
	}

	private Button createRuleButton(Composite composite, String text) {
		Button btSimpleRule = new Button(composite, SWT.RADIO);
		btSimpleRule.setText(text);
		GridData gData = new GridData();
		gData.grabExcessHorizontalSpace = true;
		btSimpleRule.setLayoutData(gData);
		return btSimpleRule;
	}

	private void addVericalGap(Composite composite) {
		Label label = new Label(composite, SWT.WRAP);
		label.setText(" ");
		label.setBounds(120, 10, 100, 100);
		
		GridData gData = new GridData();
		gData.horizontalSpan = 2;
		gData.verticalSpan = 3;
		label.setLayoutData(gData);
	}

	@Override
	public IWizardPage getNextPage() {
		if (btSimpleProxy.getSelection()) {
			return getWizard().getPage(ListenPortConfigurationPage.PAGE_NAME);
		} else if (btAdvancedProxy.getSelection()) {
			return getWizard().getPage(AdvancedProxyConfigurationPage.PAGE_NAME);
		}
		return getWizard().getPage(ProxyRuleConfigurationPage.PAGE_NAME);
	}

}
