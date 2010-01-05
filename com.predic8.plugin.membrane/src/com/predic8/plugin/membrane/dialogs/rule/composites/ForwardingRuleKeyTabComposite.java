package com.predic8.plugin.membrane.dialogs.rule.composites;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

import com.predic8.plugin.membrane.components.RuleOptionsRuleKeyGroup;

public class ForwardingRuleKeyTabComposite extends Composite {

	private RuleOptionsRuleKeyGroup ruleOptionsRuleKeyGroup;
	
	public ForwardingRuleKeyTabComposite(Composite parent) {
		super(parent, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 2;
		gridLayout.marginTop = 20;
		gridLayout.marginLeft = 20;
		gridLayout.marginBottom = 20;
		gridLayout.marginRight = 20;
		setLayout(gridLayout);
		
		ruleOptionsRuleKeyGroup = new RuleOptionsRuleKeyGroup(this, SWT.NONE);
		
	}

	public RuleOptionsRuleKeyGroup getRuleOptionsRuleKeyGroup() {
		return ruleOptionsRuleKeyGroup;
	}
	
}
