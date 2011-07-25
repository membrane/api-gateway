package com.predic8.plugin.membrane.components.composites;

import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.actions.rules.RemoveRuleAction;
import com.predic8.plugin.membrane.actions.rules.RuleEditAction;
import com.predic8.plugin.membrane.wizards.AddRuleWizard;

public class RulesViewControlsComposite extends ControlsComposite {

	private Rule selectedRule; 
	
	private RemoveRuleAction removeRuleAction;
	
	private RuleEditAction editRuleAction;
	
	public RulesViewControlsComposite(Composite parent) {
		super(parent, SWT.NONE);
		removeRuleAction = new RemoveRuleAction();
		editRuleAction = new RuleEditAction();
	}

	
	@Override
	public void newButtonPressed() {
		WizardDialog wizardDialog = new WizardDialog(Display.getCurrent().getActiveShell(), new AddRuleWizard());
		wizardDialog.create();
		wizardDialog.open();
	}
	
	@Override
	public void editButtonPressed() {
		editRuleAction.setSelectedRule(selectedRule);
		editRuleAction.run();
	}

	@Override
	public void removeButtonPressed() {
		removeRuleAction.setSelectedRule(selectedRule);
		removeRuleAction.run();
	}

	@Override
	public void upButtonPressed() {
		Router.getInstance().getRuleManager().ruleUp(selectedRule);
	}
	
	@Override
	public void downButtonPressed() {
		Router.getInstance().getRuleManager().ruleDown(selectedRule);
	}

	public void setSelectedRule(Rule selectedRule) {
		this.selectedRule = selectedRule;
	}

	
	
}
