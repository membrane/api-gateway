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
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.plugin.membrane.listeners.PortVerifyListener;

public class ListenPortConfigurationPage extends AbstractRuleWizardPage {

	public static final String PAGE_NAME = "Listen Port Configuration";
	
	private Text listenPortTextField;
	
	protected ListenPortConfigurationPage() {
		super(PAGE_NAME);
		setTitle("Simple Rule");
		setDescription("Specify Listen Port");
	}

	public void createControl(Composite parent) {
		Composite composite = createComposite(parent, 2);
		
		Label labelFullDescription = new Label(composite, SWT.WRAP);
		labelFullDescription.setText("A rule is listenening on a TCP port for incomming connections.\n" + "The port number can be any integer between 1 and 65535.");
		labelFullDescription.setBounds(120, 10, 100, 100);
		
		GridData gridData4ListenDescrLabel = new GridData();
		gridData4ListenDescrLabel.horizontalSpan = 2;
		gridData4ListenDescrLabel.verticalSpan = 2;
		labelFullDescription.setLayoutData(gridData4ListenDescrLabel);
		
		Label listenPortLabel = new Label(composite, SWT.NONE);
		GridData gridData4ListenPortLabel = new GridData();
		gridData4ListenPortLabel.horizontalSpan = 1;
		listenPortLabel.setLayoutData(gridData4ListenPortLabel);
		listenPortLabel.setText("Listen Port:");

		
		listenPortTextField = new Text(composite,SWT.BORDER);
		listenPortTextField.addVerifyListener(new PortVerifyListener());
		listenPortTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		listenPortTextField.setText(Router.getInstance().getRuleManager().getDefaultListenPort());
		listenPortTextField.addModifyListener(new ModifyListener() {
			
			public void modifyText(ModifyEvent e) {
				if (listenPortTextField.getText().trim().equals("")) {
					setPageComplete(false);
					setErrorMessage("Listen port must be specified");
				} else if (listenPortTextField.getText().trim().length() >= 5) {
					try {
						if (Integer.parseInt(listenPortTextField.getText()) > 65535) {
							setErrorMessage("Listen port number has an upper bound 65535.");
							setPageComplete(false);
						}
					} catch (NumberFormatException nfe) {
						setErrorMessage("Specified listen port must be in decimal number format.");
						setPageComplete(false);
					}
				} else {
					setErrorMessage(null);
					setPageComplete(true);
				}
			}
		});
		
		setControl(composite);
	}

	@Override
	public boolean canFlipToNextPage() {
		if (!isPageComplete())
			return false;
		try {
			if (((HttpTransport) Router.getInstance().getTransport()).isAnyThreadListeningAt(Integer.parseInt(listenPortTextField.getText()))) {
				return true;
			}
			new ServerSocket(Integer.parseInt(listenPortTextField.getText())).close();
			return true;
		} catch (IOException ex) {
			setErrorMessage("Port is already in use. Please choose a different port!");
			return false;
		} 
	}
	
	@Override
	public IWizardPage getNextPage() {
		return getWizard().getPage(TargetHostConfigurationPage.PAGE_NAME);
	}
	
	public String getListenPort() {
		return listenPortTextField.getText();
	}

	protected boolean performFinish(AddRuleWizard wizard) throws IOException {
		ForwardingRuleKey ruleKey = new ForwardingRuleKey("*", "*", ".*", Integer.parseInt(getListenPort()));
		
		if (Router.getInstance().getRuleManager().exists(ruleKey)) {
			wizard.openWarningDialog("You've entered a duplicated rule key.");
			return false;
		}
		
		
		wizard.createForwardingRule(ruleKey);
		((HttpTransport) Router.getInstance().getTransport()).openPort(ruleKey.getPort());
		return true;
	}
	
}
