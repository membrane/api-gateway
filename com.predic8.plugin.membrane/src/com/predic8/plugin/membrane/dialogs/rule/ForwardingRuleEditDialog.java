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

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.plugin.membrane.dialogs.rule.composites.ForwardingRuleKeyTabComposite;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleActionsTabComposite;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleGeneralInfoTabComposite;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleInterceptorTabComposite;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleTargetTabComposite;

public class ForwardingRuleEditDialog extends RuleEditDialog {

	private RuleTargetTabComposite targetComposite;

	private ForwardingRuleKeyTabComposite ruleKeyComposite;

	public ForwardingRuleEditDialog(Shell parentShell) {
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
		interceptorsComposite = new RuleInterceptorTabComposite(tabFolder);
		TabItem tabItem = new TabItem(tabFolder, SWT.NONE);
		tabItem.setText("Interceptors");
		tabItem.setControl(interceptorsComposite);
	}

	private void createActionComposite() {
		actionsComposite = new RuleActionsTabComposite(tabFolder);
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
		generalInfoComposite = new RuleGeneralInfoTabComposite(tabFolder);
		TabItem tabItem = new TabItem(tabFolder, SWT.NONE);
		tabItem.setText("General");
		tabItem.setControl(generalInfoComposite);
	}

	@Override
	public void setInput(Rule rule) {
		super.setInput(rule);
		ruleKeyComposite.getRuleKeyGroup().setInput(rule.getKey());
		ruleKeyComposite.setSecureConnection(rule.isInboundTSL());
		targetComposite.setInput(rule);
	}

	@Override
	public void onOkPressed() {
		ForwardingRuleKey ruleKey = ruleKeyComposite.getRuleKeyGroup().getUserInput();
		if (ruleKey == null) {
			openErrorDialog("Illeagal input! Please check again");
			return;
		}

		if (ruleKey.equals(rule.getKey())) {
			try {
				updateRule(ruleKey, false);
			} catch (IOException e) {
				openErrorDialog("Can not open port. Please check again");
			}
			getRuleManager().ruleChanged(rule);
			return;
		}

		if (getRuleManager().exists(ruleKey)) {
			openErrorDialog("Illeagal input! Your rule key conflict with another existent rule.");
			return;
		}
		if (!openConfirmDialog("You've changed the rule key, so all the old history will be cleared."))
			return;

		if (!getTransport().isAnyThreadListeningAt(ruleKey.getPort())) {
			try {
				getTransport().openPort(ruleKey.getPort(), rule.isInboundTSL());
			} catch (IOException e1) {
				openErrorDialog("Failed to open the new port. Please change another one. Old rule is retained");
				return;
			}
		}
		getRuleManager().removeRule(rule);
		if (!getRuleManager().isAnyRuleWithPort(rule.getKey().getPort()) && (rule.getKey().getPort() != ruleKey.getPort())) {
			try {
				getTransport().closePort(rule.getKey().getPort());
			} catch (IOException e2) {
				openErrorDialog("Failed to close the obsolete port: " + rule.getKey().getPort());
			}
		}
		try {
			updateRule(ruleKey, true);
		} catch (IOException e) {
			openErrorDialog("Can not open port. Please check again");
		}
		getRuleManager().ruleChanged(rule);

	}

	private RuleManager getRuleManager() {
		return Router.getInstance().getRuleManager();
	}

	private HttpTransport getTransport() {
		return ((HttpTransport) Router.getInstance().getTransport());
	}

	private void updateRule(ForwardingRuleKey ruleKey, boolean addToManager) throws IOException {
		rule.setName(generalInfoComposite.getRuleName());
		rule.setKey(ruleKey);
		if (addToManager) {
			getRuleManager().addRuleIfNew(rule);
		}
		((ForwardingRule) rule).setTargetHost(targetComposite.getTargetGroup().getTargetHost());
		((ForwardingRule) rule).setTargetPort(targetComposite.getTargetGroup().getTargetPort());
		rule.setOutboundTSL(targetComposite.getSecureConnection());
		rule.setInboundTSL(ruleKeyComposite.getSecureConnection());
		rule.setBlockRequest(actionsComposite.isRequestBlocked());
		rule.setBlockResponse(actionsComposite.isResponseBlocked());
		rule.setInterceptors(interceptorsComposite.getInterceptors());
	}

}
