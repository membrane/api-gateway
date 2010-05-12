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
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.predic8.membrane.core.Router;
import com.predic8.plugin.membrane.listeners.PortVerifyListener;

public class TargetConfigurationPage extends AbstractRuleWizardPage {

	public static final String PAGE_NAME = "Target Configuration";
	
	private Text ruleTargetHostText;
	
	private Text ruleTargetPortText;
	
	private Button btSecureConnection;
	
	protected TargetConfigurationPage() {
		super(PAGE_NAME);
		setTitle("Simple Rule");
		setDescription("Specify Target Host and Port");
	}

	public void createControl(Composite parent) {
		Composite composite = createComposite(parent, 1);
		
		createFullDescriptionLabel(composite, "If this rule applies to an incomming message Membrane Monitor will" + "\nforward the message to the target host on the specified port number.");
		
		createSecureConnectionButton(composite);
		
		Group group = createTargetGroup(composite);
		
		new Label(group, SWT.NONE).setText("Host:");

		ruleTargetHostText = createRuletargetHostText(group);

		addLabelGap(group);
		
		new Label(group, SWT.NONE).setText("Port");

		ruleTargetPortText = createRuletargetPortText(group);
		
		addLabelGap(group);
	
		setControl(composite);
	}
	
	private void createSecureConnectionButton(Composite composite) {
		btSecureConnection = new Button(composite, SWT.CHECK);
		btSecureConnection.setText("SecureConnection (SSL/STL)");
		btSecureConnection.setEnabled(Router.getInstance().getConfigurationManager().getConfiguration().isSecurityConfigurationAvailable());
	}

	private Text createRuletargetHostText(Group ruleTargetGroup) {
		final Text text = new Text(ruleTargetGroup, SWT.BORDER);
		text.setText(Router.getInstance().getRuleManager().getDefaultTargetHost());
		text.addModifyListener(new ModifyListener(){
			public void modifyText(ModifyEvent e) {
				if (text.getText().trim().equals("")) {
					setPageComplete(false);
					setErrorMessage("Target host must be specified");
				} else {
					setPageComplete(true);
					setErrorMessage(null);
				}
			}});
		text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		return text;
	}

	private void addLabelGap(Group ruleTargetGroup) {
		GridData gData = new GridData(GridData.FILL_HORIZONTAL);
		
		Label lb1 = new Label(ruleTargetGroup, SWT.NONE);
		lb1.setLayoutData(gData);
		lb1.setText(" ");
		
		Label lb2 = new Label(ruleTargetGroup, SWT.NONE);
		lb2.setLayoutData(gData);
		lb2.setText(" ");
	}

	
	
	private Group createTargetGroup(Composite composite) {
		Group ruleTargetGroup = new Group(composite, SWT.NONE);
		ruleTargetGroup.setText("Target");
		ruleTargetGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));
		GridLayout gridLayout4TargetGroup = new GridLayout();
		gridLayout4TargetGroup.numColumns = 4;
		ruleTargetGroup.setLayout(gridLayout4TargetGroup);
		return ruleTargetGroup;
	}

	private Text createRuletargetPortText(Group ruleTargetGroup) {
		final Text text = new Text(ruleTargetGroup,SWT.BORDER);
		text.setText(Router.getInstance().getRuleManager().getDefaultTargetPort());
		text.addVerifyListener(new PortVerifyListener());
		text.addModifyListener(new ModifyListener(){
			public void modifyText(ModifyEvent e) {
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
			}});
		text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		return text;
	}

	@Override
	public IWizardPage getNextPage() {
		return null;
	}
	
	public String getTargetHost() {
		return ruleTargetHostText.getText();
	}

	public boolean getSecureConnection() {
		return btSecureConnection.getSelection();
	}
	
	public String getTargetPort() {
		return ruleTargetPortText.getText();
	}
	
	@Override
	public boolean canFinish() {
		return true;
	}

	@Override
	boolean performFinish(AddRuleWizard wizard) throws IOException {
		if (getPreviousPage().getName().equals(ListenPortConfigurationPage.PAGE_NAME)) {
			return wizard.listenPortConfigPage.performFinish(wizard);
		} 
		
		if (getPreviousPage().getName().equals(AdvancedRuleConfigurationPage.PAGE_NAME)) {
			
			if(wizard.checkIfSimilarRuleExists())
				return false;
			
			wizard.addRule();
		}
		return true;
	}

}
