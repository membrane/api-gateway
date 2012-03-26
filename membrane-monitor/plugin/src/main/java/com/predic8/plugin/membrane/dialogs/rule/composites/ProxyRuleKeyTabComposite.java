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

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;

import com.predic8.membrane.core.rules.*;
import com.predic8.plugin.membrane.listeners.PortVerifyListener;
import com.predic8.plugin.membrane.util.SWTUtil;

public class ProxyRuleKeyTabComposite extends AbstractProxyFeatureComposite {

	protected Text textListenPort;
	
	public ProxyRuleKeyTabComposite(Composite parent) {
		super(parent);
		setLayout(SWTUtil.createGridLayout(2, 20));
		
		new Label(this, SWT.NONE).setText("Listen Port: ");
		
		textListenPort = createText();
	}

	private Text createText() {
		Text text = new Text(this, SWT.BORDER);
		text.addVerifyListener(new PortVerifyListener());
		text.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				Text t = (Text)e.widget;
				if (!t.getText().equals("" + rule.getKey().getPort())) {
					dataChanged = true;
				}
			}
		});
		
		GridData gData = new GridData();
		gData.widthHint = 150;
		text.setLayoutData(gData);
		return text;
	}

	@Override
	public void setRule(Rule rule) {
		super.setRule(rule);
		textListenPort.setText(Integer.toString(rule.getKey().getPort()));
	}
	
	public String getListenPort() {
		return textListenPort.getText().trim();
	}

	@Override
	public String getTitle() {
		return "Proxy Key";
	}
	
	@Override
	public void commit() {
		if (rule == null)
			return;
		
		int port = 0;
		try {
			port = Integer.parseInt(getListenPort());
		} catch (NumberFormatException nfe) {
			MessageDialog.openError(this.getShell(), "Error", "Illeagal input! Please check listen port again");
			return;
		}

		ProxyRuleKey ruleKey = new ProxyRuleKey(port);
		rule.setKey(ruleKey);
	}
	
	@Override
	public boolean isDataChanged() {
		// TODO Auto-generated method stub
		return super.isDataChanged();
	}
}
