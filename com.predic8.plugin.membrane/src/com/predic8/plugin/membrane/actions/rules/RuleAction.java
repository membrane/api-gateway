package com.predic8.plugin.membrane.actions.rules;

import org.eclipse.jface.action.Action;

import com.predic8.membrane.core.rules.Rule;

public abstract class RuleAction extends Action {

	protected Rule selectedRule;
	
	public RuleAction(String id, String text) {
		setId(id);
		setText(text);
		setEnabled(false);
	}
	
	public void setSelectedRule(Rule selectedRule) {
		this.selectedRule = selectedRule;
	}
	
}
