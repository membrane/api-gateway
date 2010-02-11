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

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.ProxyRuleKey;
import com.predic8.plugin.membrane.listeners.PortVerifyListener;

public class ProxyRuleConfigurationPage extends AbstractRuleWizardPage {

	public static final String PAGE_NAME = "Proxy Rule Configuration";

	private Text ruleOptionsListenPortTextField;

	protected ProxyRuleConfigurationPage() {
		super(PAGE_NAME);
		setTitle("Proxy Rule");
		setDescription("Specify Listen Port for Proxy Rule");
	}

	public void createControl(Composite parent) {
		Composite composite = createComposite(parent, 2);

		Label labelFullDescription = new Label(composite, SWT.WRAP);
		labelFullDescription
				.setText("A rule is listenening on a TCP port for incomming connections.\n"
						+ "The port number can be any integer between 1 and 65535.");
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

		ruleOptionsListenPortTextField = new Text(composite, SWT.BORDER);
		ruleOptionsListenPortTextField
				.addVerifyListener(new PortVerifyListener());
		ruleOptionsListenPortTextField.setLayoutData(new GridData(
				GridData.FILL_HORIZONTAL));
		ruleOptionsListenPortTextField.setText(getRuleManager().getDefaultListenPort());
		ruleOptionsListenPortTextField.addModifyListener(new ModifyListener() {

			public void modifyText(ModifyEvent e) {
				if (ruleOptionsListenPortTextField.getText().trim().equals("")) {
					setPageComplete(false);
					setErrorMessage("Listen port must be specified");
				} else if (ruleOptionsListenPortTextField.getText().trim()
						.length() >= 5) {
					try {
						if (Integer.parseInt(ruleOptionsListenPortTextField
								.getText()) > 65535) {
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
	public IWizardPage getNextPage() {
		return null;
	}

	public int getListenPort() {
		return Integer.parseInt(ruleOptionsListenPortTextField.getText());
	}

	@Override
	public boolean canFinish() {
		return true;
	}

	@Override
	boolean performFinish(AddRuleWizard wizard) throws IOException {
		ProxyRuleKey key = new ProxyRuleKey(getListenPort());
		if (getRuleManager().exists(key)) {
			wizard.openWarningDialog("You've entered a duplicated rule key.");
			return false;
		}

		getRuleManager().addRuleIfNew(new ProxyRule(key));
		getHttpTransport().openPort(key.getPort());
		return true;
	}

}
