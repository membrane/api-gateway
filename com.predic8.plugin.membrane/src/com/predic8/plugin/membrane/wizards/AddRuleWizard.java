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


import java.io.IOException;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.Wizard;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class AddRuleWizard extends Wizard {

	private RuleTypeSelectionPage selectionWizardPage = new RuleTypeSelectionPage();

	ListenPortConfigurationPage listenPortConfigPage = new ListenPortConfigurationPage();

	private TargetHostConfigurationPage targetHostConfigPage = new TargetHostConfigurationPage();

	AdvancedRuleConfigurationPage advancedRuleConfigPage = new AdvancedRuleConfigurationPage();

	private ProxyRuleConfigurationPage proxyRuleConfigPage = new ProxyRuleConfigurationPage();
	
	public AddRuleWizard() {
		setWindowTitle("Add Rule ...");
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
			return getCurrentPage().performFinish(this);
		} catch (Exception ex) {
			openWarningDialog(ex.getMessage());
			return false;
		}
	}

	void createForwardingRule(ForwardingRuleKey ruleKey) {
		ForwardingRule rule = new ForwardingRule();
		rule.setTargetHost(targetHostConfigPage.getTargetHost());
		rule.setTargetPort(targetHostConfigPage.getTargetPort());
		rule.setKey(ruleKey);

		getRuleManager().addRuleIfNew(rule);
	}

	@Override
	public boolean performCancel() {
		return true;
	}

	@Override
	public boolean canFinish() {
		return getCurrentPage().canFinish();		
	}

	private AbstractRuleWizardPage getCurrentPage() {
		return ((AbstractRuleWizardPage)getContainer().getCurrentPage());
	}

	void openWarningDialog(String msg) {
		MessageDialog.openWarning(this.getShell(), "Warning", msg);
	}
	
	private int getListenPort() {
		return Integer.parseInt(advancedRuleConfigPage.getListenPort());
	}

	private String getListenHost() {
		return advancedRuleConfigPage.getListenHost();
	}

	private String getMethod() {
		return advancedRuleConfigPage.getMethod();
	}

	ForwardingRuleKey getRuleKey() {
		if (advancedRuleConfigPage.getUsePathPatter()) {
			ForwardingRuleKey key = new ForwardingRuleKey(getListenHost(), getMethod(), advancedRuleConfigPage.getPathPattern(), getListenPort());
			key.setUsePathPattern(true);
			key.setPathRegExp(advancedRuleConfigPage.isRegExp());
			return key;
		} 
		return  new ForwardingRuleKey(getListenHost(), getMethod(), ".*", getListenPort());
	}

	boolean checkIfSimilarRuleExists() {
		if (getRuleManager().exists(getRuleKey())) {
			openWarningDialog("You've entered a duplicated rule key.");
			return true;
		}
		return false;
	}
	
	protected RuleManager getRuleManager() {
		return Router.getInstance().getRuleManager();
	}

	protected HttpTransport getHttpTransport() {
		return ((HttpTransport) Router.getInstance().getTransport());
	}
	
	void addRule() throws IOException {
		createForwardingRule(getRuleKey());
		getHttpTransport().openPort(getRuleKey().getPort());
	}

}
