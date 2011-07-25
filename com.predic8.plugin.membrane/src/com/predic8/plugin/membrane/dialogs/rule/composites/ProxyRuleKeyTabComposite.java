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
package com.predic8.plugin.membrane.dialogs.rule.composites;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.plugin.membrane.components.GridPanel;

public class ProxyRuleKeyTabComposite extends GridPanel {

	protected Text textListenPort;
	
	public ProxyRuleKeyTabComposite(Composite parent) {
		super(parent, 20, 2);
		
		Label lbListenPort = new Label(this, SWT.NONE);
		lbListenPort.setText("Listen Port: ");
		
		textListenPort = new Text(this, SWT.BORDER);
		GridData gridData4PortText = new GridData();
		gridData4PortText.widthHint = 150;
		textListenPort.setLayoutData(gridData4PortText);
	}

	public void setInput(RuleKey ruleKey) {
		if (ruleKey == null)
			return;
		textListenPort.setText(Integer.toString(ruleKey.getPort()));
	}
	
	public String getListenPort() {
		return textListenPort.getText().trim();
	}
}
