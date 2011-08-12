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
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;

import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.plugin.membrane.dialogs.rule.composites.ForwardingRuleKeyTabComposite;
import com.predic8.plugin.membrane.dialogs.rule.composites.ProxyActionsTabComposite;
import com.predic8.plugin.membrane.dialogs.rule.composites.ProxyGeneralInfoTabComposite;
import com.predic8.plugin.membrane.dialogs.rule.composites.ProxyInterceptorTabComposite;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleTargetTabComposite;

public class ServiceProxyEditDialog extends AbstractProxyEditDialog {

	private RuleTargetTabComposite targetComposite;

	private ForwardingRuleKeyTabComposite ruleKeyComposite;

	public ServiceProxyEditDialog(Shell parentShell) {
		super(parentShell);

	}

	@Override
	public String getTitle() {
		return "Edit Forwarding Rule";
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Control comp = super.createDialogArea(parent);

		createGeneralComposite();

		createRuleKeyComposite();

		createTargetComposite();

		createActionComposite();

		createInterceptorsComposite();

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
		
		return comp;
	}

	private void createInterceptorsComposite() {
		interceptorsComposite = new ProxyInterceptorTabComposite(tabFolder);
		TabItem tabItem = new TabItem(tabFolder, SWT.NONE);
		tabItem.setText("Interceptors");
		tabItem.setControl(interceptorsComposite);
	}

	private void createActionComposite() {
		actionsComposite = new ProxyActionsTabComposite(tabFolder);
		TabItem tabItem = new TabItem(tabFolder, SWT.NONE);
		tabItem.setText("Actions");
		tabItem.setControl(actionsComposite);
	}

	private void createTargetComposite() {
		targetComposite = new RuleTargetTabComposite(tabFolder);
		TabItem tabItem = new TabItem(tabFolder, SWT.NONE);
		tabItem.setText("Target");
		tabItem.setControl(targetComposite);
	}

	private void createRuleKeyComposite() {
		ruleKeyComposite = new ForwardingRuleKeyTabComposite(tabFolder);
		TabItem tabItem = new TabItem(tabFolder, SWT.NONE);
		tabItem.setText("Rule Key");
		tabItem.setControl(ruleKeyComposite);
	}

	private void createGeneralComposite() {
		generalInfoComposite = new ProxyGeneralInfoTabComposite(tabFolder);
		TabItem tabItem = new TabItem(tabFolder, SWT.NONE);
		tabItem.setText("General");
		tabItem.setControl(generalInfoComposite);
	}

	@Override
	public void setInput(Rule rule) {
		super.setInput(rule);
		ruleKeyComposite.getRuleKeyGroup().setInput(rule.getKey());
		ruleKeyComposite.setSecureConnection(rule.isInboundTLS());
		targetComposite.setInput(rule);
	}

	@Override
	public void onOkPressed() {
		ForwardingRuleKey ruleKey = ruleKeyComposite.getRuleKeyGroup().getUserInput();
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
