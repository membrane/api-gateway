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
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.predic8.membrane.core.Core;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.RuleKey;



public class RuleOptionsRuleKeyGroup {

	
	private Group ruleOptionsRuleKeyGroup;

	private GridLayout gridLayout4RuleKeyGroup;

	private Label ruleOptionsListenPortLabel;

	private GridData gridData4ListenPortLabel;

	private GridData gridData4ListenHostLabel;
	
	private Text ruleOptionsListenPortTextField;

	private Label ruleOptionsMethodLabel;

	private GridData gridData4MethodLabel;

	private Combo ruleOptionsMethodCombo;

	private Text ruleOptionsPathTextField;

	private Text ruleOptionsHostTextField;
	
	private Label ruleOptionsPathLabel;

	private Label ruleOptionsHostLabel;
	
	private GridData gridData4PathTextField;
	
	private GridData gridData4HostTextField;

	public RuleOptionsRuleKeyGroup(Composite parent, int style) {
		
		ruleOptionsRuleKeyGroup = new Group(parent, style);
		ruleOptionsRuleKeyGroup.setText("Rule Key");
		ruleOptionsRuleKeyGroup.setLayoutData(new GridData( GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));

		gridLayout4RuleKeyGroup = new GridLayout();
		gridLayout4RuleKeyGroup.numColumns = 5;
		ruleOptionsRuleKeyGroup.setLayout(gridLayout4RuleKeyGroup);

		
		ruleOptionsHostLabel = new Label(ruleOptionsRuleKeyGroup, SWT.NONE);
		gridData4ListenHostLabel = new GridData();
		gridData4ListenHostLabel.horizontalSpan = 2;
		ruleOptionsHostLabel.setLayoutData(gridData4ListenHostLabel);
		ruleOptionsHostLabel.setText("Host:");

		ruleOptionsHostTextField = new Text(ruleOptionsRuleKeyGroup, SWT.BORDER);
		gridData4HostTextField = new GridData(GridData.FILL_HORIZONTAL);
		ruleOptionsHostTextField.setLayoutData(gridData4HostTextField);
		ruleOptionsHostTextField.setText(Core.getRuleManager().getDefaultHost());
		
		
		Label ruleOptionsTargetHostLabelDummy0 = new Label(ruleOptionsRuleKeyGroup, SWT.NONE);
		GridData gridDataForLabelDummy0 = new GridData(GridData.FILL_HORIZONTAL);
		ruleOptionsTargetHostLabelDummy0.setLayoutData(gridDataForLabelDummy0);
		ruleOptionsTargetHostLabelDummy0.setText(" ");
	
		Label ruleOptionsTargetHostLabelDummy1 = new Label(ruleOptionsRuleKeyGroup, SWT.NONE);
		GridData gridDataForLabelDummy1 = new GridData(GridData.FILL_HORIZONTAL);
		ruleOptionsTargetHostLabelDummy1.setLayoutData(gridDataForLabelDummy1);
		ruleOptionsTargetHostLabelDummy1.setText(" ");
		
		
		
		ruleOptionsListenPortLabel = new Label(ruleOptionsRuleKeyGroup, SWT.NONE);
		gridData4ListenPortLabel = new GridData();
		gridData4ListenPortLabel.horizontalSpan = 2;
		ruleOptionsListenPortLabel.setLayoutData(gridData4ListenPortLabel);
		ruleOptionsListenPortLabel.setText("Listen Port:");

		
		ruleOptionsListenPortTextField = new Text(ruleOptionsRuleKeyGroup,SWT.BORDER);
		ruleOptionsListenPortTextField.addVerifyListener(new PortVerifyListener());
		ruleOptionsListenPortTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		ruleOptionsListenPortTextField.setText(Core.getRuleManager().getDefaultListenPort());
		
		
		Label ruleOptionsTargetHostLabelDummy2 = new Label(ruleOptionsRuleKeyGroup, SWT.NONE);
		GridData gridDataForLabelDummy2 = new GridData(GridData.FILL_HORIZONTAL);
		ruleOptionsTargetHostLabelDummy2.setLayoutData(gridDataForLabelDummy2);
		ruleOptionsTargetHostLabelDummy2.setText(" ");
		
		Label ruleOptionsTargetHostLabelDummy3 = new Label(ruleOptionsRuleKeyGroup, SWT.NONE);
		GridData gridDataForLabelDummy3 = new GridData(GridData.FILL_HORIZONTAL);
		ruleOptionsTargetHostLabelDummy3.setLayoutData(gridDataForLabelDummy3);
		ruleOptionsTargetHostLabelDummy3.setText(" ");
		
		ruleOptionsMethodLabel = new Label(ruleOptionsRuleKeyGroup, SWT.NONE);
		gridData4MethodLabel = new GridData();
		gridData4MethodLabel.horizontalSpan = 2;
		ruleOptionsMethodLabel.setLayoutData(gridData4MethodLabel);
		ruleOptionsMethodLabel.setText("Method:");

		ruleOptionsMethodCombo = new Combo(ruleOptionsRuleKeyGroup, SWT.READ_ONLY);
		ruleOptionsMethodCombo.setItems(new String[] { "POST", "GET", "DELETE", "PUT", " * " });
		ruleOptionsMethodCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		ruleOptionsMethodCombo.select(Core.getRuleManager().getDefaultMethod());
		
		Label ruleOptionsTargetHostLabelDummy4 = new Label(ruleOptionsRuleKeyGroup, SWT.NONE);
		GridData gridDataForLabelDummy4 = new GridData(GridData.FILL_HORIZONTAL);
		ruleOptionsTargetHostLabelDummy4.setLayoutData(gridDataForLabelDummy4);
		ruleOptionsTargetHostLabelDummy4.setText(" ");
	
		Label ruleOptionsTargetHostLabelDummy5 = new Label(ruleOptionsRuleKeyGroup, SWT.NONE);
		GridData gridDataForLabelDummy5 = new GridData(GridData.FILL_HORIZONTAL);
		ruleOptionsTargetHostLabelDummy5.setLayoutData(gridDataForLabelDummy5);
		ruleOptionsTargetHostLabelDummy5.setText(" ");
		
		ruleOptionsPathLabel = new Label(ruleOptionsRuleKeyGroup, SWT.NONE);
		ruleOptionsPathLabel.setText("Path:");

		ruleOptionsPathTextField = new Text(ruleOptionsRuleKeyGroup, SWT.BORDER);
		gridData4PathTextField = new GridData(GridData.FILL_HORIZONTAL);
		gridData4PathTextField.horizontalSpan = 4;
		ruleOptionsPathTextField.setLayoutData(gridData4PathTextField);
		ruleOptionsPathTextField.setText(Core.getRuleManager().getDefaultPath());
	}


	public void clear() {
		ruleOptionsListenPortTextField.setText("");
		ruleOptionsPathTextField.setText("");
		ruleOptionsHostTextField.setText("");
		ruleOptionsMethodCombo.clearSelection();
	}

	public ForwardingRuleKey getUserInput() {
		String port = ruleOptionsListenPortTextField.getText().trim();
        String host = ruleOptionsHostTextField.getText().trim();
		int index = ruleOptionsMethodCombo.getSelectionIndex();
		String method;
		if (index > -1)
			method = ruleOptionsMethodCombo.getItem(index);
		else
			method = "";

		String path = ruleOptionsPathTextField.getText().trim();
		if (port.length() == 0 || method.length() == 0 || path.length() == 0) {
			return null;
		}

		return new ForwardingRuleKey(host, method, path, Integer.parseInt(port));
	}

	public void setUserInput(RuleKey ruleKey) {
		ruleOptionsListenPortTextField.setText(Integer.toString(ruleKey.getPort()));

		String method = ruleKey.getMethod();
		String[] methods = ruleOptionsMethodCombo.getItems();
		for (int i = 0; i < methods.length; i ++) {
			if (method.trim().equals(methods[i].trim())) {
				ruleOptionsMethodCombo.select(i);
				break;
			}
		}

		ruleOptionsPathTextField.setText(ruleKey.getPath());
		ruleOptionsHostTextField.setText(ruleKey.getHost());
	}

}