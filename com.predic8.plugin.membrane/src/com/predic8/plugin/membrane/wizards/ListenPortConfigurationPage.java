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
import org.eclipse.swt.widgets.Composite;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class ListenPortConfigurationPage extends AbstractPortConfigurationPage {

	public static final String PAGE_NAME = "Listen Port Configuration";
	
	protected ListenPortConfigurationPage() {
		super(PAGE_NAME);
		setTitle("Simple Proxy");
		setDescription("Specify Listen Port");
	}

	public void createControl(Composite parent) {
		Composite composite = createComposite(parent, 2);
		
		createFullDescriptionLabel(composite, "A proxy is listenening on a TCP port for incomming connections.\n" + "The port number can be any integer between 1 and 65535.");
		
		createListenPortLabel(composite);
		
		listenPortText = createListenPortText(composite);
		
		setControl(composite);
	}

	@Override
	public boolean canFlipToNextPage() {
		if (!isPageComplete())
			return false;
		try {
			if (((HttpTransport) Router.getInstance().getTransport()).isAnyThreadListeningAt(Integer.parseInt(listenPortText.getText()))) {
				return true;
			}
			new ServerSocket(Integer.parseInt(listenPortText.getText())).close();
			return true;
		} catch (IOException ex) {
			setErrorMessage("Port is already in use. Please choose a different port!");
			return false;
		} 
	}
	
	@Override
	public IWizardPage getNextPage() {
		IWizardPage page = getWizard().getPage(TargetConfigurationPage.PAGE_NAME);
		//page is used in simple and advanced configuration, therefore title must be adjusted
		page.setTitle("Simple Proxy"); 
		return page;
	}
	
	protected boolean performFinish(AddProxyWizard wizard) throws IOException {
		ForwardingRuleKey ruleKey = new ForwardingRuleKey("*", "*", ".*", getListenPort());
		
		if (Router.getInstance().getRuleManager().exists(ruleKey)) {
			wizard.openWarningDialog("You've entered a duplicated rule key.");
			return false;
		}
		
		
		wizard.createForwardingRule(ruleKey);
		return true;
	}
	
}
