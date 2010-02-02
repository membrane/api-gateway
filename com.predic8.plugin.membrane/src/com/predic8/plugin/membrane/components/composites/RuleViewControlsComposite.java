package com.predic8.plugin.membrane.components.composites;

import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.wizards.AddRuleWizard;

public class RuleViewControlsComposite extends ControlsComposite {

	private Rule selectedRule; 
	
	public RuleViewControlsComposite(Composite parent) {
		super(parent, SWT.NONE);
		
	}

	
	@Override
	public void newButtonPressed() {
		WizardDialog wizardDialog = new WizardDialog(Display.getCurrent().getActiveShell(), new AddRuleWizard());
		wizardDialog.create();
		wizardDialog.open();
	}
	
	@Override
	public void editButtonPressed() {

	}

	@Override
	public void removeButtonPressed() {
		

	}

	@Override
	public void upButtonPressed() {
		Router.getInstance().getRuleManager().ruleDown(selectedRule);
	}
	
	@Override
	public void downButtonPressed() {
		Router.getInstance().getRuleManager().ruleDown(selectedRule);
	}

	public void setSelectedRule(Rule selectedRule) {
		this.selectedRule = selectedRule;
	}

}
