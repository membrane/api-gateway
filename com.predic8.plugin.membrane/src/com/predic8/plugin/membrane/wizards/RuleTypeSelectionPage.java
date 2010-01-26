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
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

public class RuleTypeSelectionPage extends WizardPage {

	public static final String PAGE_NAME = "Type Selection";
	
	private Button btSimpleRule;
	
	private Button btAdvancedRule;
	
	private Button btProxyRule;
	
	protected RuleTypeSelectionPage() {
		super(PAGE_NAME);
		setTitle(" Add new Rule for a ");
	}

	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 2;
		gridLayout.marginTop = 10;
		gridLayout.marginLeft = 2;
		gridLayout.marginBottom = 10;
		gridLayout.marginRight = 10;
		gridLayout.verticalSpacing = 20;
		composite.setLayout(gridLayout);
		
		
		btSimpleRule = new Button(composite, SWT.RADIO);
		btSimpleRule.setText("Simple Reverse Proxy");
		GridData btSimpleGridData = new GridData();
		btSimpleGridData.grabExcessHorizontalSpace = true;
		btSimpleRule.setLayoutData(btSimpleGridData);
		
		
		Label labelFullDescription = new Label(composite, SWT.WRAP);
		labelFullDescription.setText("Create rule to forward  HTTP and SOAP over HTTP requests.");
		labelFullDescription.setBounds(120, 10, 100, 100);
		
		GridData gridData4ListenDescrLabel = new GridData();
		gridData4ListenDescrLabel.horizontalSpan = 2;
		gridData4ListenDescrLabel.verticalSpan = 1;
		labelFullDescription.setLayoutData(gridData4ListenDescrLabel);
		
		
		Label labelGap = new Label(composite, SWT.WRAP);
		labelGap.setText(" ");
		labelGap.setBounds(120, 10, 100, 100);
		
		GridData gridData4LabelGap = new GridData();
		gridData4LabelGap.horizontalSpan = 2;
		gridData4LabelGap.verticalSpan = 3;
		labelGap.setLayoutData(gridData4LabelGap);
		
		
		
		btAdvancedRule = new Button(composite, SWT.RADIO);
		btAdvancedRule.setText("Advanced Reverse Proxy");
		GridData btAdvancedGridData = new GridData();
		btAdvancedGridData.grabExcessHorizontalSpace = true;
		btAdvancedRule.setLayoutData(btAdvancedGridData);
		
		
		Label labelFullDescriptionAdvanced = new Label(composite, SWT.WRAP);
		labelFullDescriptionAdvanced.setText("Offers all available options for reverse proxy rules like virtual host,\nHTTP method and request URL.");
		labelFullDescriptionAdvanced.setBounds(120, 10, 100, 100);
		
		GridData gridData4ListenDescrLabelAdvanced = new GridData();
		gridData4ListenDescrLabelAdvanced.horizontalSpan = 2;
		gridData4ListenDescrLabelAdvanced.verticalSpan = 2;
		labelFullDescriptionAdvanced.setLayoutData(gridData4ListenDescrLabelAdvanced);
		
		
		Label labelGap1 = new Label(composite, SWT.WRAP);
		labelGap1.setText(" ");
		labelGap1.setBounds(120, 10, 100, 100);
		
		GridData gridData4LabelGap1 = new GridData();
		gridData4LabelGap1.horizontalSpan = 2;
		gridData4LabelGap1.verticalSpan = 3;
		labelGap1.setLayoutData(gridData4LabelGap1);
		
		

		btProxyRule = new Button(composite, SWT.RADIO);
		btProxyRule.setText("HTTP  Proxy Rule");
		GridData btProxyGridData = new GridData();
		btProxyGridData.grabExcessHorizontalSpace = true;
		btProxyRule.setLayoutData(btProxyGridData);

		
		Label labelFullDescriptionProxy = new Label(composite, SWT.WRAP);
		labelFullDescriptionProxy.setText("Works like a regular HTTP Proxy.\nCan proxy SOAP and HTTP requests.");
		labelFullDescriptionProxy.setBounds(120, 10, 100, 100);
		
		GridData gridData4ListenDescrLabelProxy = new GridData();
		gridData4ListenDescrLabelProxy.horizontalSpan = 2;
		gridData4ListenDescrLabelProxy.verticalSpan = 2;
		labelFullDescriptionProxy.setLayoutData(gridData4ListenDescrLabelProxy);
		
		
		setControl(composite);
	}

	@Override
	public IWizardPage getNextPage() {
		if (btSimpleRule.getSelection()) {
			return getWizard().getPage(ListenPortConfigurationPage.PAGE_NAME);
		} else if (btAdvancedRule.getSelection()) {
			return getWizard().getPage(AdvancedRuleConfigurationPage.PAGE_NAME);
		}
		return getWizard().getPage(ProxyRuleConfigurationPage.PAGE_NAME);
	}
	
}
