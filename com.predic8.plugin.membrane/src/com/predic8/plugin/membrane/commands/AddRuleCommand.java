package com.predic8.plugin.membrane.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Display;

import com.predic8.plugin.membrane.wizards.AddRuleWizard;


public class AddRuleCommand extends AbstractHandler {

	public Object execute(ExecutionEvent event) throws ExecutionException {
		//TODO copied from RulesViewControlsComposite, does the same as clicking the Button
		WizardDialog wizardDialog = new WizardDialog(Display.getCurrent().getActiveShell(), new AddRuleWizard());
		wizardDialog.create();
		wizardDialog.open();
		return null;
	}

}
