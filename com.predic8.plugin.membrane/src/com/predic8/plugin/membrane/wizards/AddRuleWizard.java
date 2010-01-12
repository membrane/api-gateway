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
				System.out.println("previous page: " + targetHostConfigPage.getPreviousPage().getName());
				if (targetHostConfigPage.getPreviousPage().getName().equals(ListenPortConfigurationPage.PAGE_NAME)) {
					int listenPort = Integer.parseInt(listenPortConfigPage.getListenPort());
					
					ForwardingRuleKey ruleKey = new ForwardingRuleKey("*", "*", ".*", listenPort);
					
					if (Router.getInstance().getRuleManager().getRule(ruleKey) != null) {
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
					System.out.println("method set is: " + method);
					boolean usePattern = advancedRuleConfigPage.getUsePathPatter();
					ForwardingRuleKey ruleKey = null;
					if (usePattern) {
						ruleKey = new ForwardingRuleKey(listenHost, method, advancedRuleConfigPage.getPathPattern(), listenPort);
						ruleKey.setUsePathPattern(true);
						ruleKey.setPathRegExp(advancedRuleConfigPage.isRegExp());
					} else {
						ruleKey = new ForwardingRuleKey(listenHost, method, ".*", listenPort);
					}

					if (Router.getInstance().getRuleManager().getRule(ruleKey) != null) {
						openWarningDialog("You've entered a duplicated rule key.");
						return false;
					}
					
					createForwardingRule(ruleKey);
					((HttpTransport) Router.getInstance().getTransport()).openPort(ruleKey.getPort());
					return true;
				}
			} else if (getContainer().getCurrentPage().getName().equals(ProxyRuleConfigurationPage.PAGE_NAME)) {
				int listenPort = Integer.parseInt(proxyRuleConfigPage.getListenPort());
				
				ProxyRuleKey ruleKey = new ProxyRuleKey(listenPort);
				if (Router.getInstance().getRuleManager().getRule(ruleKey) != null) {
					openWarningDialog("You've entered a duplicated rule key.");
					return false;
				}
				
				ProxyRule rule = new ProxyRule(ruleKey);
				
				Router.getInstance().getRuleManager().addRuleIfNew(rule);
				((HttpTransport) Router.getInstance().getTransport()).openPort(ruleKey.getPort());
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
		rule.setRuleKey(ruleKey);

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
