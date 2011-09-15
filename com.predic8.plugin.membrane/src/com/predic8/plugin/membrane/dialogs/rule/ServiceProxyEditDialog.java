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


package com.predic8.plugin.membrane.dialogs.rule;

import java.io.IOException;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.events.*;
import org.eclipse.swt.widgets.*;

import com.predic8.membrane.core.rules.*;
import com.predic8.plugin.membrane.dialogs.rule.composites.*;

public class ServiceProxyEditDialog extends AbstractProxyEditDialog {

	private ServiceProxyTargetTabComposite targetComposite;

	private ServiceProxyKeyTabComposite ruleKeyComposite;

	public ServiceProxyEditDialog(Shell parentShell) {
		super(parentShell);

	}

	@Override
	public String getTitle() {
		return "Edit Service Proxy";
	}

	@Override
	protected void createExtensionTabs() {
		super.createExtensionTabs();
		createTargetTab();
	}
	
	@Override
	protected void createRuleKeyComposite() {
		ruleKeyComposite = new ServiceProxyKeyTabComposite(tabFolder);
	}
	
	@Override
	protected Composite getRuleKeyComposite() {
		return ruleKeyComposite;
	}
	
	private void createTargetTab() {
		createTargetTabComposite();
		createTabItem("Target", targetComposite);
	}

	private void createTargetTabComposite() {
		targetComposite = new ServiceProxyTargetTabComposite(tabFolder);
		targetComposite.getTargetGroup().getTextTargetHost().addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				Text t = (Text) e.getSource();
				if ("".equals(t.getText().trim())) {
					getButton(IDialogConstants.OK_ID).setEnabled(false);
					return;
				}

				if (!targetComposite.getTargetGroup().isValidHostNameInput()) {
					getButton(IDialogConstants.OK_ID).setEnabled(false);
					return;
				}
				getButton(IDialogConstants.OK_ID).setEnabled(true);
			}
		});
	}
	
	@Override
	public void setInput(Rule rule) {
		super.setInput(rule);
		ruleKeyComposite.setInput(rule);
		targetComposite.setInput(rule);
	}

	@Override
	public void onOkPressed() {
		ServiceProxyKey ruleKey = ruleKeyComposite.getUserInput();
		if (ruleKey == null) {
			openErrorDialog("Illeagal input! Please check again");
			return;
		}

		doRuleUpdate(ruleKey);
	}

	@Override
	protected void updateProxy(RuleKey ruleKey, boolean addToManager) throws IOException {
		((ServiceProxy) rule).setTargetHost(targetComposite.getTargetGroup().getTargetHost());
		((ServiceProxy) rule).setTargetPort(Integer.parseInt(targetComposite.getTargetGroup().getTargetPort()));
		rule.setOutboundTLS(targetComposite.getSecureConnection());
		rule.setInboundTLS(ruleKeyComposite.getSecureConnection());
		super.updateProxy(ruleKey, addToManager);
	}

}
