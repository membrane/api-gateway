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
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.predic8.membrane.core.Router;
import com.predic8.plugin.membrane.components.PortVerifyListener;

public class TargetHostConfigurationPage extends WizardPage {

	public static final String PAGE_NAME = "Target Host Configuration";
	
	private Text ruleOptionsTargetHostTextField;
	
	private Text ruleOptionsTargetPortTextField;
	
	protected TargetHostConfigurationPage() {
		super(PAGE_NAME);
		setTitle("Simple Rule");
		setDescription("Specify Target Host and Port");
	}

	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		gridLayout.marginTop = 10;
		gridLayout.marginLeft = 2;
		gridLayout.marginBottom = 10;
		gridLayout.marginRight = 10;
		gridLayout.verticalSpacing = 20;
		composite.setLayout(gridLayout);
		
		
		Label labelFullDescription = new Label(composite, SWT.WRAP);
		labelFullDescription.setText("If this rule applies to an incomming message Membrane Monitor will" +
				"\nforward the message to the target host on the specified port number.");
		labelFullDescription.setBounds(120, 10, 100, 100);
		
		GridData gridData4ListenDescrLabel = new GridData();
		gridData4ListenDescrLabel.horizontalSpan = 2;
		gridData4ListenDescrLabel.verticalSpan = 2;
		labelFullDescription.setLayoutData(gridData4ListenDescrLabel);
		
		
		Group ruleTargetGroup = new Group(composite, SWT.NONE);
		ruleTargetGroup.setText("Target");
		ruleTargetGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));
		GridLayout gridLayout4TargetGroup = new GridLayout();
		gridLayout4TargetGroup.numColumns = 4;
		ruleTargetGroup.setLayout(gridLayout4TargetGroup);
		
		
		new Label(ruleTargetGroup, SWT.NONE).setText("Host:");

		ruleOptionsTargetHostTextField = new Text(ruleTargetGroup, SWT.BORDER);
		ruleOptionsTargetHostTextField.setText(Router.getInstance().getRuleManager().getDefaultTargetHost());
		ruleOptionsTargetHostTextField.addModifyListener(new ModifyListener(){
			public void modifyText(ModifyEvent e) {
				if (ruleOptionsTargetHostTextField.getText().trim().equals("")) {
					setPageComplete(false);
					setErrorMessage("Target host must be specified");
				} else {
					setPageComplete(true);
					setErrorMessage(null);
				}
			}});
		ruleOptionsTargetHostTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		
		
		Label labelDummy3 = new Label(ruleTargetGroup, SWT.NONE);
		GridData gridDataForDummy3 = new GridData(GridData.FILL_HORIZONTAL);
		labelDummy3.setLayoutData(gridDataForDummy3);
		labelDummy3.setText(" ");
		
		Label labelDummy4 = new Label(ruleTargetGroup, SWT.NONE);
		GridData gridDataForLabelDummy7 = new GridData(GridData.FILL_HORIZONTAL);
		labelDummy4.setLayoutData(gridDataForLabelDummy7);
		labelDummy4.setText(" ");
		
		Label targetPortTextLabel = new Label(ruleTargetGroup, SWT.NONE);
		targetPortTextLabel.setText("Port");

		ruleOptionsTargetPortTextField = new Text(ruleTargetGroup,SWT.BORDER);
		ruleOptionsTargetPortTextField.setText(Router.getInstance().getRuleManager().getDefaultTargetPort());
		ruleOptionsTargetPortTextField.addVerifyListener(new PortVerifyListener());
		ruleOptionsTargetPortTextField.addModifyListener(new ModifyListener(){
			public void modifyText(ModifyEvent e) {
				if (ruleOptionsTargetPortTextField.getText().trim().equals("")) {
					setPageComplete(false);
					setErrorMessage("Target host port must be specified");
				} else if (ruleOptionsTargetPortTextField.getText().trim().length() >= 5) {
					try {
						if (Integer.parseInt(ruleOptionsTargetPortTextField.getText()) > 65535) {
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
		ruleOptionsTargetPortTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		Label labelDummy5 = new Label(ruleTargetGroup, SWT.NONE);
		GridData gridDataForLabelDummy5 = new GridData(GridData.FILL_HORIZONTAL);
		labelDummy5.setLayoutData(gridDataForLabelDummy5);
		labelDummy5.setText(" ");
	
		Label labelDummy6 = new Label(ruleTargetGroup, SWT.NONE);
		GridData gridDataForLabelDummy6 = new GridData(GridData.FILL_HORIZONTAL);
		labelDummy6.setLayoutData(gridDataForLabelDummy6);
		labelDummy6.setText(" ");
		
		
		setControl(composite);
	}
	
	@Override
	public IWizardPage getNextPage() {
		return null;
	}
	
	public String getTargetHost() {
		return ruleOptionsTargetHostTextField.getText();
	}

	public String getTargetPort() {
		return ruleOptionsTargetPortTextField.getText();
	}
	
	
	
}
