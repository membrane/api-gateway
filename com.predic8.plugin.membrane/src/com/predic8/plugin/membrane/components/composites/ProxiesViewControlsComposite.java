package com.predic8.plugin.membrane.components.composites;

import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.actions.rules.RemoveProxyAction;
import com.predic8.plugin.membrane.actions.rules.EditProxyAction;
import com.predic8.plugin.membrane.wizards.AddProxyWizard;

public class ProxiesViewControlsComposite extends ControlsComposite {

	private Rule selectedRule; 
	
	private RemoveProxyAction removeRuleAction;
	
	private EditProxyAction editRuleAction;
	
	public ProxiesViewControlsComposite(Composite parent) {
		super(parent, SWT.NONE);
		removeRuleAction = new RemoveProxyAction();
		editRuleAction = new EditProxyAction();
	}

	
	@Override
	public void newButtonPressed() {
		WizardDialog wizardDialog = new WizardDialog(Display.getCurrent().getActiveShell(), new AddProxyWizard());
		wizardDialog.create();
		wizardDialog.open();
	}
	
	@Override
	public void editButtonPressed() {
		editRuleAction.setSelectedProxy(selectedRule);
		editRuleAction.run();
	}

	@Override
	public void removeButtonPressed() {
		removeRuleAction.setSelectedProxy(selectedRule);
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
