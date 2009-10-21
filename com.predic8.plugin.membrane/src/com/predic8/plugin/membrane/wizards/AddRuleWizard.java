package com.predic8.plugin.membrane.wizards;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.Wizard;

import com.predic8.membrane.core.Core;
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

				int listenPort = Integer.parseInt(listenPortConfigPage.getListenPort());
				String targetHost = targetHostConfigPage.getTargetHost();
				String targetPort = targetHostConfigPage.getTargetPort();

				ForwardingRuleKey ruleKey = new ForwardingRuleKey("*", "*", ".*", listenPort);
				if (Core.getRuleManager().getRule(ruleKey) != null) {
					openWarningDialog("You've entered a duplicated rule key.");
					return false;
				}
				
				
				ForwardingRule rule = new ForwardingRule();
				rule.setTargetHost(targetHost);
				rule.setTargetPort(targetPort);
				rule.setRuleKey(ruleKey);

				Core.getRuleManager().addRuleIfNew(rule);
				((HttpTransport) Core.getTransport()).openPort(ruleKey.getPort());
				return true;

			} else if (getContainer().getCurrentPage().getName().equals(AdvancedRuleConfigurationPage.PAGE_NAME)) {
				String listenHost = advancedRuleConfigPage.getListenHost().trim().equals("") ? "*" : advancedRuleConfigPage.getListenHost();
				int listenPort = Integer.parseInt(advancedRuleConfigPage.getListenPort());
				String path = advancedRuleConfigPage.getPath().trim().equals("") ? ".*" : advancedRuleConfigPage.getPath();
				String method = advancedRuleConfigPage.getMethod();
				
				ForwardingRuleKey ruleKey = new ForwardingRuleKey(listenHost, method, path, listenPort);
				if (Core.getRuleManager().getRule(ruleKey) != null) {
					openWarningDialog("You've entered a duplicated rule key.");
					return false;
				}
				
				ForwardingRule rule = new ForwardingRule();
				rule.setRuleKey(ruleKey);
				rule.setTargetHost(advancedRuleConfigPage.getTargetHost());
				rule.setTargetPort(advancedRuleConfigPage.getTargetHostPort());
				Core.getRuleManager().addRuleIfNew(rule);
				((HttpTransport) Core.getTransport()).openPort(ruleKey.getPort());
				return true;
			} else if (getContainer().getCurrentPage().getName().equals(ProxyRuleConfigurationPage.PAGE_NAME)) {
				int listenPort = Integer.parseInt(proxyRuleConfigPage.getListenPort());
				
				ProxyRuleKey ruleKey = new ProxyRuleKey(listenPort);
				if (Core.getRuleManager().getRule(ruleKey) != null) {
					openWarningDialog("You've entered a duplicated rule key.");
					return false;
				}
				
				ProxyRule rule = new ProxyRule(ruleKey);
				
				Core.getRuleManager().addRuleIfNew(rule);
				((HttpTransport) Core.getTransport()).openPort(ruleKey.getPort());
				return true;
			}
		} catch (Exception ex) {
			openWarningDialog(ex.getMessage());
			return false;
		}
		return true;
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
		
		return super.canFinish();
	}

	void openWarningDialog(String msg) {
		MessageDialog.openWarning(this.getShell(), "Warning", msg);
	}

}
