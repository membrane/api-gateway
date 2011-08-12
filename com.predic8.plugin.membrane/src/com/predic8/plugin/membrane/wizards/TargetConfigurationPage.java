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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import com.predic8.plugin.membrane.components.RuleTargetGroup;

public class TargetConfigurationPage extends SecurityWizardPage {

	public static final String PAGE_NAME = "Target Configuration";

	private boolean canFinish = true;

	RuleTargetGroup ruleTargetGroup;
	
	protected TargetConfigurationPage() {
		super(PAGE_NAME);
		setTitle("Simple Rule");
		setDescription("Specify Target Host and Port");
	}
	
	public void createControl(Composite parent) {
		Composite composite = createComposite(parent, 1);
		
		createFullDescriptionLabel(composite, "If this rule applies to an incomming message Membrane Monitor will" + "\nforward the message to the target host on the specified port number.");

		createSecurityComposite(composite);

		ruleTargetGroup = new RuleTargetGroup(composite, SWT.NONE);
		
		ruleTargetGroup.getTextTargetHost().addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {

				Text t = (Text) e.getSource();

				if ("".equals(t.getText().trim())) {
					canFinish = false;
					setPageComplete(false);
					setErrorMessage("Target host must be specified");
					return;
				}

				if (ruleTargetGroup.isValidHostNameInput()) {
					canFinish = true;
					setPageComplete(true);
					setErrorMessage(null);
					return;
				}

				canFinish = false;
				setPageComplete(false);
				setErrorMessage("Target host name is invalid");

			}
		});
		
		ruleTargetGroup.getTextTargetPort().addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				
				Text text = (Text)e.getSource();
				
				if (text.getText().trim().equals("")) {
					setPageComplete(false);
					setErrorMessage("Target host port must be specified");
				} else if (text.getText().trim().length() >= 5) {
					try {
						if (Integer.parseInt(text.getText()) > 65535) {
							setErrorMessage("Target host port number has an upper bound 65535.");
							setPageComplete(false);
						}
					} catch (NumberFormatException nfe) {
						setErrorMessage("Specified target host port must be in decimal number format.");
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

	public String getTargetHost() {
		return ruleTargetGroup.getTargetHost();
	}

	public String getTargetPort() {
		return ruleTargetGroup.getTargetPort();
	}

	@Override
	public boolean canFinish() {
		return canFinish;
	}

	@Override
	boolean performFinish(AddProxyWizard wizard) throws IOException {
		if (getPreviousPage().getName().equals(ListenPortConfigurationPage.PAGE_NAME)) {
			return wizard.listenPortConfigPage.performFinish(wizard);
		}

		if (getPreviousPage().getName().equals(AdvancedProxyConfigurationPage.PAGE_NAME)) {

			if (wizard.checkIfSimilarRuleExists())
				return false;

			wizard.addProxy();
		}
		return true;
	}

	@Override
	protected void addListenersToSecureConnectionButton() {
		// do nothing
	}

}
