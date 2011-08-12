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

import java.io.IOException;
import java.net.ServerSocket;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.plugin.membrane.components.RuleKeyGroup;

public class AdvancedProxyConfigurationPage extends SecurityWizardPage {

	public static final String PAGE_NAME = "Advanced Proxy Configuration";
	
	RuleKeyGroup ruleKeyGroup;
	
	protected AdvancedProxyConfigurationPage() {
		super(PAGE_NAME);
		setTitle("Advanced Proxy");
		setDescription("Specify all proxy configuration parameters");
	}

	public void createControl(Composite parent) {
		Composite composite = createComposite(parent, 1);
		
		createSecurityComposite(composite);
		
		ruleKeyGroup = new RuleKeyGroup(composite, SWT.NONE);
		
		setControl(composite);
	}
	
	@Override
	protected void addListenersToSecureConnectionButton() {
		btSecureConnection.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (btSecureConnection.getSelection())
					ruleKeyGroup.getTextListenPort().setText("443");
			}
		});
	}

	public String getListenPort() {
		return ruleKeyGroup.getTextListenPort().getText();
	}
	
	public String getListenHost() {
		return ruleKeyGroup.getTextRuleHost().getText();
	}
	
	public String getMethod() {
		int index = ruleKeyGroup.getComboRuleMethod().getSelectionIndex();		
		if (index == 4) 
			return "*";
		if (index > -1)
			return ruleKeyGroup.getComboRuleMethod().getItem(index);
		return "";
	}
	
	@Override
	public IWizardPage getNextPage() {
		IWizardPage page = getWizard().getPage(TargetConfigurationPage.PAGE_NAME);
		//page is used in simple and advanced configuration, therefore title must be adjusted
		page.setTitle("Advanced Proxy");
		return page;
	}
	
	@Override
	public boolean canFlipToNextPage() {
		if (!isPageComplete())
			return false;
		try {
			if (getTransport().isAnyThreadListeningAt(Integer.parseInt(ruleKeyGroup.getTextListenPort().getText()))) {
				return true;
			}
			new ServerSocket(Integer.parseInt(ruleKeyGroup.getTextListenPort().getText())).close();
			return true;
		} catch (IOException ex) {
			setErrorMessage("Port is already in use. Please choose a different port!");
			return false;
		} 
	}
	
	private HttpTransport getTransport() {
		return ((HttpTransport) Router.getInstance().getTransport());
	}

	public boolean getUsePathPatter() {
		return ruleKeyGroup.getBtPathPattern().getSelection();
	}
		
	public boolean isRegExp() {
		return ruleKeyGroup.getBtRegExp().getSelection();
	}
	
	public String getPathPattern() {
		return ruleKeyGroup.getTextRulePath().getText();
	}
}
