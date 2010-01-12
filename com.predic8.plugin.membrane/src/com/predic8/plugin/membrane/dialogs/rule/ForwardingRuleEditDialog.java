package com.predic8.plugin.membrane.dialogs.rule;

import java.io.IOException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabItem;

import com.predic8.membrane.core.Router;
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
		
		generalInfoComposite = new RuleGeneralInfoTabComposite(tabFolder);
		TabItem generalTabItem = new TabItem(tabFolder, SWT.NONE);
		generalTabItem.setText("General");
		generalTabItem.setControl(generalInfoComposite);
		
		ruleKeyComposite = new ForwardingRuleKeyTabComposite(tabFolder);
		TabItem keyTabItem = new TabItem(tabFolder, SWT.NONE);
		keyTabItem.setText("Rule Key");
		keyTabItem.setControl(ruleKeyComposite);
		
		
		targetComposite = new RuleTargetTabComposite(tabFolder);
		TabItem targetTabItem = new TabItem(tabFolder, SWT.NONE);
		targetTabItem.setText("Target");
		targetTabItem.setControl(targetComposite);
		
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
		ruleKeyComposite.getRuleOptionsRuleKeyGroup().setInput(rule.getRuleKey());
		targetComposite.setInput(rule);
	}

	@Override
	public void onOkPressed() {
		String targetHost = targetComposite.getTargetGroup().getTargetHost();
		String targetPort = targetComposite.getTargetGroup().getTargetPort();
		boolean blockRequest = actionsComposite.isRequestBlocked();
		boolean blockResponse = actionsComposite.isResponseBlocked();

		ForwardingRuleKey ruleKey = ruleKeyComposite.getRuleOptionsRuleKeyGroup().getUserInput();
		if (ruleKey == null) {
			openErrorDialog("Illeagal input! Please check again");
			return;
		}

		if (ruleKey.equals(rule.getRuleKey())) {
			rule.setRuleKey(ruleKey); //equals considers only listen port
			rule.setName(generalInfoComposite.getRuleName());
			((ForwardingRule)rule).setTargetHost(targetHost);
			((ForwardingRule)rule).setTargetPort(targetPort);
			rule.setBlockRequest(blockRequest);
			rule.setBlockResponse(blockResponse);
			rule.setInterceptors(interceptorsComposite.getInterceptorList());
			Router.getInstance().getRuleManager().ruleChanged(rule);
			return;
		}

		if (Router.getInstance().getRuleManager().getRule(ruleKey) != null) {
			openErrorDialog("Illeagal input! Your rule key conflict with another existent rule.");
			return;
		}
		if (openConfirmDialog("You've changed the rule key, so all the old history will be cleared.")) {

			if (!((HttpTransport) Router.getInstance().getTransport()).isAnyThreadListeningAt(ruleKey.getPort())) {
				try {
					((HttpTransport) Router.getInstance().getTransport()).addPort(ruleKey.getPort());
				} catch (IOException e1) {
					openErrorDialog("Failed to open the new port. Please change another one. Old rule is retained");
					return;
				}
			}
			Router.getInstance().getRuleManager().removeRule(rule);
			if (!Router.getInstance().getRuleManager().isAnyRuleWithPort(rule.getRuleKey().getPort()) && (rule.getRuleKey().getPort() != ruleKey.getPort())) {
				try {
					((HttpTransport) Router.getInstance().getTransport()).closePort(rule.getRuleKey().getPort());
				} catch (IOException e2) {
					openErrorDialog("Failed to close the obsolete port: " + rule.getRuleKey().getPort());
				}
			}
			rule.setName(generalInfoComposite.getRuleName());
			rule.setRuleKey(ruleKey);
			Router.getInstance().getRuleManager().addRuleIfNew(rule);
			((ForwardingRule)rule).setTargetHost(targetHost);
			((ForwardingRule)rule).setTargetPort(targetPort);
			rule.setBlockRequest(blockRequest);
			rule.setBlockResponse(blockResponse);
			rule.setInterceptors(interceptorsComposite.getInterceptorList());
			Router.getInstance().getRuleManager().ruleChanged(rule);
		}
	}
	
}
