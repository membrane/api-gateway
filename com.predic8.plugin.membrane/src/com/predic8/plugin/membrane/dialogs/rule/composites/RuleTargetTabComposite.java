package com.predic8.plugin.membrane.dialogs.rule.composites;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.components.RuleOptionsTargetGroup;

public class RuleTargetTabComposite extends Composite {

	private RuleOptionsTargetGroup targetGroup;
	
	public RuleTargetTabComposite(Composite parent) {
		super(parent, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		gridLayout.marginTop = 20;
		gridLayout.marginLeft = 20;
		gridLayout.marginBottom = 20;
		gridLayout.marginRight = 20;
		setLayout(gridLayout);
		
		targetGroup = new RuleOptionsTargetGroup(this, SWT.NONE);
		
	}

	public RuleOptionsTargetGroup getTargetGroup() {
		return targetGroup;
	}

	public void setInput(Rule rule) {
		if (!(rule instanceof ForwardingRule))
			return;
		ForwardingRule fRule = (ForwardingRule)rule;
		targetGroup.setTargetHost(fRule.getTargetHost());
		targetGroup.setTargetPort(fRule.getTargetPort());
	}
	
}
