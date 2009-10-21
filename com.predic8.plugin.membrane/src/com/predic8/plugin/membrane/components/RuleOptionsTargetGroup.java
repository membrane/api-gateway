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

package com.predic8.plugin.membrane.components;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.predic8.membrane.core.Core;



public class RuleOptionsTargetGroup {
	
	private Group ruleOptionsTargetGroup;

	private Text ruleOptionsTargetPortTextField;

	private Text ruleOptionsTargetHostTextField;

	private Label ruleOptionsTargetHostLabel;

	private Label ruleOptionsTargetPortTextLabel;

	private GridLayout gridLayout4TargetGroup;

	public RuleOptionsTargetGroup(Composite parent, int style) {
		ruleOptionsTargetGroup = new Group(parent, style);
		ruleOptionsTargetGroup.setText("Target");
		ruleOptionsTargetGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));
		gridLayout4TargetGroup = new GridLayout();
		gridLayout4TargetGroup.numColumns = 4;
		ruleOptionsTargetGroup.setLayout(gridLayout4TargetGroup);

		ruleOptionsTargetHostLabel = new Label(ruleOptionsTargetGroup, SWT.NONE);
		ruleOptionsTargetHostLabel.setText("Host");

		ruleOptionsTargetHostTextField = new Text(ruleOptionsTargetGroup, SWT.BORDER);
		ruleOptionsTargetHostTextField.setText(Core.getRuleManager().getDefaultTargetHost());
		ruleOptionsTargetHostTextField.addModifyListener(new ModifyListener(){
			public void modifyText(ModifyEvent e) {
				//RuleOptionsTargetGroup.this.parent.setEnableOnlyModifyAndRestoreButton(true);
			}});
		ruleOptionsTargetHostTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Label ruleOptionsTargetHostLabelDummy1 = new Label(ruleOptionsTargetGroup, SWT.NONE);
		GridData gridDataForLabelDummy1 = new GridData(GridData.FILL_HORIZONTAL);
		ruleOptionsTargetHostLabelDummy1.setLayoutData(gridDataForLabelDummy1);
		ruleOptionsTargetHostLabelDummy1.setText(" ");
		
		Label ruleOptionsTargetHostLabelDummy2 = new Label(ruleOptionsTargetGroup, SWT.NONE);
		GridData gridDataForLabelDummy2 = new GridData(GridData.FILL_HORIZONTAL);
		ruleOptionsTargetHostLabelDummy2.setLayoutData(gridDataForLabelDummy2);
		ruleOptionsTargetHostLabelDummy2.setText(" ");
		
		ruleOptionsTargetPortTextLabel = new Label(ruleOptionsTargetGroup,
				SWT.NONE);
		ruleOptionsTargetPortTextLabel.setText("Port");

		ruleOptionsTargetPortTextField = new Text(ruleOptionsTargetGroup,SWT.BORDER);
		ruleOptionsTargetPortTextField.setText(Core.getRuleManager().getDefaultTargetPort());
		ruleOptionsTargetPortTextField.addVerifyListener(new PortVerifyListener());
		ruleOptionsTargetPortTextField.addModifyListener(new ModifyListener(){
			public void modifyText(ModifyEvent e) {
				//RuleOptionsTargetGroup.this.parent.setEnableOnlyModifyAndRestoreButton(true);
			}});
		ruleOptionsTargetPortTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		Label ruleOptionsTargetHostLabelDummy3 = new Label(ruleOptionsTargetGroup, SWT.NONE);
		GridData gridDataForLabelDummy3 = new GridData(GridData.FILL_HORIZONTAL);
		ruleOptionsTargetHostLabelDummy3.setLayoutData(gridDataForLabelDummy3);
		ruleOptionsTargetHostLabelDummy3.setText(" ");
	
		Label ruleOptionsTargetHostLabelDummy4 = new Label(ruleOptionsTargetGroup, SWT.NONE);
		GridData gridDataForLabelDummy4 = new GridData(GridData.FILL_HORIZONTAL);
		ruleOptionsTargetHostLabelDummy4.setLayoutData(gridDataForLabelDummy4);
		ruleOptionsTargetHostLabelDummy4.setText(" ");
		
	}


	public void clear() {
		ruleOptionsTargetHostTextField.setText("");
		ruleOptionsTargetPortTextField.setText("");
	}

	public String getTargetHost() {
		return ruleOptionsTargetHostTextField.getText().trim();
	}

	public String getTargetPort() {
		return ruleOptionsTargetPortTextField.getText().trim();
	}

	public void setTargetHost(String host) {
		ruleOptionsTargetHostTextField.setText(host);
	}

	public void setTargetPort(String port) {
		ruleOptionsTargetPortTextField.setText(port);
	}

	public void setTargetPort(int port) {
		ruleOptionsTargetPortTextField.setText(Integer.toString(port));
	}
	
}