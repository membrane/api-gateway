package com.predic8.plugin.membrane.views;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.components.RuleDetailsComposite;

public class RuleDetailsView extends ViewPart {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.RuleDetailsView";
	
	private RuleDetailsComposite ruleDetailsComposite;
	
	public RuleDetailsView() {
		
	}

	@Override
	public void createPartControl(Composite parent) {
		ruleDetailsComposite = new RuleDetailsComposite(parent);
	}

	@Override
	public void setFocus() {
		ruleDetailsComposite.setFocus();
	}

	public void setRuleToDisplay(Rule rule) {
		ruleDetailsComposite.configure(rule);
	}
	
}
