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

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.Wizard;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.ProxyRuleKey;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class AddRuleWizard extends Wizard {

	private RuleTypeSelectionPage selectionWizardPage;

	private ListenPortConfigurationPage listenPortConfigPage;

	private TargetHostConfigurationPage targetHostConfigPage;

	private AdvancedRuleConfigurationPage advancedRuleConfigPage;

	private ProxyRuleConfigurationPage proxyRuleConfigPage;
	
	public AddRuleWizard() {
		setWindowTitle("Add Rule ...");
		selectionWizardPage = new RuleTypeSelectionPage();
		listenPortConfigPage = new ListenPortConfigurationPage();
		advancedRuleConfigPage = new AdvancedRuleConfigurationPage();
		targetHostConfigPage = new TargetHostConfigurationPage();
		proxyRuleConfigPage = new ProxyRuleConfigurationPage();
	}

	@Override
	public void addPages() {
		addPage(selectionWizardPage);
		addPage(listenPortConfigPage);
		addPage(advancedRuleConfigPage);
		addPage(targetHostConfigPage);
		addPage(proxyRuleConfigPage);
	}

	@Override
	public boolean performFinish() {
		try {
			if (getContainer().getCurrentPage().getName().equals(TargetHostConfigurationPage.PAGE_NAME)) {
				
				if (targetHostConfigPage.getPreviousPage().getName().equals(ListenPortConfigurationPage.PAGE_NAME)) {
					int listenPort = Integer.parseInt(listenPortConfigPage.getListenPort());
					
					ForwardingRuleKey ruleKey = new ForwardingRuleKey("*", "*", ".*", listenPort);
					
					if (Router.getInstance().getRuleManager().exists(ruleKey)) {
						openWarningDialog("You've entered a duplicated rule key.");
						return false;
					}
					
					
					createForwardingRule(ruleKey);
					((HttpTransport) Router.getInstance().getTransport()).openPort(ruleKey.getPort());
					return true;
				} else if (targetHostConfigPage.getPreviousPage().getName().equals(AdvancedRuleConfigurationPage.PAGE_NAME)) {
					int listenPort = Integer.parseInt(advancedRuleConfigPage.getListenPort());
					
					String listenHost = advancedRuleConfigPage.getListenHost();
					String method = advancedRuleConfigPage.getMethod();
					
					boolean usePattern = advancedRuleConfigPage.getUsePathPatter();
					ForwardingRuleKey key = null;
					if (usePattern) {
						key = new ForwardingRuleKey(listenHost, method, advancedRuleConfigPage.getPathPattern(), listenPort);
						key.setUsePathPattern(true);
						key.setPathRegExp(advancedRuleConfigPage.isRegExp());
					} else {
						key = new ForwardingRuleKey(listenHost, method, ".*", listenPort);
					}

					if (Router.getInstance().getRuleManager().exists(key)) {
						openWarningDialog("You've entered a duplicated rule key.");
						return false;
					}
					
					createForwardingRule(key);
					((HttpTransport) Router.getInstance().getTransport()).openPort(key.getPort());
					return true;
				}
			} else if (getContainer().getCurrentPage().getName().equals(ProxyRuleConfigurationPage.PAGE_NAME)) {
				int listenPort = Integer.parseInt(proxyRuleConfigPage.getListenPort());
				
				ProxyRuleKey key = new ProxyRuleKey(listenPort);
				if (Router.getInstance().getRuleManager().exists(key)) {
					openWarningDialog("You've entered a duplicated rule key.");
					return false;
				}
				
				ProxyRule rule = new ProxyRule(key);
				
				Router.getInstance().getRuleManager().addRuleIfNew(rule);
				((HttpTransport) Router.getInstance().getTransport()).openPort(key.getPort());
				return true;
			}
		} catch (Exception ex) {
			openWarningDialog(ex.getMessage());
			return false;
		}
		return true;
	}

	private void createForwardingRule(ForwardingRuleKey ruleKey) {
		ForwardingRule rule = new ForwardingRule();
		rule.setTargetHost(targetHostConfigPage.getTargetHost());
		rule.setTargetPort(targetHostConfigPage.getTargetPort());
		rule.setKey(ruleKey);

		Router.getInstance().getRuleManager().addRuleIfNew(rule);
	}

	@Override
	public boolean performCancel() {
		return true;
	}

	@Override
	public boolean canFinish() {
		if (getContainer().getCurrentPage().getName().equals(RuleTypeSelectionPage.PAGE_NAME))
			return false;
		
		if (getContainer().getCurrentPage().getName().equals(ListenPortConfigurationPage.PAGE_NAME))
			return false;
		
		if (getContainer().getCurrentPage().getName().equals(AdvancedRuleConfigurationPage.PAGE_NAME))
			return false;
		
		return super.canFinish();
	}

	void openWarningDialog(String msg) {
		MessageDialog.openWarning(this.getShell(), "Warning", msg);
	}

}
