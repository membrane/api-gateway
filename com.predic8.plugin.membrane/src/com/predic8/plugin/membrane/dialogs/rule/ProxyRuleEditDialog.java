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

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabItem;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.rules.ProxyRuleKey;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleActionsTabComposite;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleGeneralInfoTabComposite;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleInterceptorTabComposite;
import com.predic8.plugin.membrane.dialogs.rule.composites.ProxyRuleKeyTabComposite;

public class ProxyRuleEditDialog extends RuleEditDialog {

	private ProxyRuleKeyTabComposite ruleKeyComposite;

	public ProxyRuleEditDialog(Shell parentShell) {
		super(parentShell);
	}

	@Override
	public String getTitle() {
		return "Edit Proxy Rule";
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Control comp = super.createDialogArea(parent);

		generalInfoComposite = new RuleGeneralInfoTabComposite(tabFolder);
		TabItem generalTabItem = new TabItem(tabFolder, SWT.NONE);
		generalTabItem.setText("General");
		generalTabItem.setControl(generalInfoComposite);

		ruleKeyComposite = new ProxyRuleKeyTabComposite(tabFolder);
		TabItem keyTabItem = new TabItem(tabFolder, SWT.NONE);
		keyTabItem.setText("Rule Key");
		keyTabItem.setControl(ruleKeyComposite);

		actionsComposite = new RuleActionsTabComposite(tabFolder);
		TabItem actionsTabItem = new TabItem(tabFolder, SWT.NONE);
		actionsTabItem.setText("Actions");
		actionsTabItem.setControl(actionsComposite);

		interceptorsComposite = new RuleInterceptorTabComposite(tabFolder);
		TabItem interceptorsTabItem = new TabItem(tabFolder, SWT.NONE);
		interceptorsTabItem.setText("Interceptors");
		interceptorsTabItem.setControl(interceptorsComposite);

		return comp;
	}

	@Override
	public void setInput(Rule rule) {
		super.setInput(rule);
		ruleKeyComposite.setInput(rule.getKey());
	}

	@Override
	public void onOkPressed() {
		int port = 0;
		try {
			port = Integer.parseInt(ruleKeyComposite.getListenPort());
		} catch (NumberFormatException nfe) {
			openErrorDialog("Illeagal input! Please check listen port again");
			return;
		}

		ProxyRuleKey key = new ProxyRuleKey(port);
		if (key.equals(rule.getKey())) {
			rule.setName(generalInfoComposite.getRuleName());
			rule.setInterceptors(interceptorsComposite.getInterceptors());
			getRuleManager().ruleChanged(rule);
			return;
		}

		if (getRuleManager().exists(key)) {
			openErrorDialog("Illeagal input! Your rule key conflict with another existent rule.");
			return;
		}

		if (!openConfirmDialog("You've changed the rule key, so all the old history will be cleared."))
			return;

		if (!(getTransport()).isAnyThreadListeningAt(key.getPort())) {
			try {
				(getTransport()).addPort(key.getPort());
			} catch (IOException e1) {
				openErrorDialog("Failed to open the new port. Please change another one. Old rule is retained");
				return;
			}
		}
		getRuleManager().removeRule(rule);
		if (!getRuleManager().isAnyRuleWithPort(rule.getKey().getPort()) && (rule.getKey().getPort() != key.getPort())) {
			try {
				(getTransport()).closePort(rule.getKey().getPort());
			} catch (IOException e2) {
				openErrorDialog("Failed to close the obsolete port: " + rule.getKey().getPort());
			}
		}
		rule.setName(generalInfoComposite.getRuleName());
		rule.setKey(key);
		rule.setInterceptors(interceptorsComposite.getInterceptors());
		getRuleManager().addRuleIfNew(rule);
		getRuleManager().ruleChanged(rule);

	}

	private RuleManager getRuleManager() {
		return Router.getInstance().getRuleManager();
	}

	private HttpTransport getTransport() {
		return (HttpTransport) Router.getInstance().getTransport();
	}

}
