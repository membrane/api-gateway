package com.predic8.plugin.membrane.dialogs.rule;

import java.io.IOException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabItem;

import com.predic8.membrane.core.Router;
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
		ruleKeyComposite.setInput(rule.getRuleKey());
	}

	@Override
	public void onOkPressed() {
		try {
			int port = Integer.parseInt(ruleKeyComposite.getListenPort());
			ProxyRuleKey key = new ProxyRuleKey(port);
			if (key.equals(rule.getRuleKey())) {
				rule.setName(generalInfoComposite.getRuleName());
				rule.setInterceptors(interceptorsComposite.getInterceptorList());
				Router.getInstance().getRuleManager().ruleChanged(rule);
				return;
			}
			
			if (Router.getInstance().getRuleManager().getRule(key) != null) {
				openErrorDialog("Illeagal input! Your rule key conflict with another existent rule.");
				return;
			}
			
			if (openConfirmDialog("You've changed the rule key, so all the old history will be cleared.")) {

				if (!((HttpTransport) Router.getInstance().getTransport()).isAnyThreadListeningAt(key.getPort())) {
					try {
						((HttpTransport) Router.getInstance().getTransport()).addPort(key.getPort());
					} catch (IOException e1) {
						openErrorDialog("Failed to open the new port. Please change another one. Old rule is retained");
						return;
					}
				}
				Router.getInstance().getRuleManager().removeRule(rule);
				if (!Router.getInstance().getRuleManager().isAnyRuleWithPort(rule.getRuleKey().getPort()) && (rule.getRuleKey().getPort() != key.getPort())) {
					try {
						((HttpTransport) Router.getInstance().getTransport()).closePort(rule.getRuleKey().getPort());
					} catch (IOException e2) {
						openErrorDialog("Failed to close the obsolete port: " + rule.getRuleKey().getPort());
					}
				}
				rule.setName(generalInfoComposite.getRuleName());
				rule.setRuleKey(key);
				rule.setInterceptors(interceptorsComposite.getInterceptorList());
				Router.getInstance().getRuleManager().addRuleIfNew(rule);
				Router.getInstance().getRuleManager().ruleChanged(rule);
			}
		} catch (NumberFormatException nfe) {
			openErrorDialog("Illeagal input! Please check listen port again");
			return;
		}
	}
	
}
