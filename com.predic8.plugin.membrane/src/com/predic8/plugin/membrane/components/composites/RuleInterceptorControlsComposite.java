package com.predic8.plugin.membrane.components.composites;

import org.eclipse.swt.SWT;

import com.predic8.plugin.membrane.dialogs.rule.composites.RuleInterceptorTabComposite;

public class RuleInterceptorControlsComposite extends ControlsComposite {
	
	
	public RuleInterceptorControlsComposite(RuleInterceptorTabComposite parent) {
		super(parent, SWT.NONE);
	}

	@Override
	public void downButtonPressed() {
		((RuleInterceptorTabComposite)getParent()).moveDownSelectedInterceptor();
	}

	@Override
	public void editButtonPressed() {
		((RuleInterceptorTabComposite)getParent()).editSelectedInterceptor();
	}

	@Override
	public void newButtonPressed() {
		((RuleInterceptorTabComposite)getParent()).addNewInterceptor();
	}

	@Override
	public void removeButtonPressed() {
		((RuleInterceptorTabComposite)getParent()).removeSelectedInterceptor();
	}

	@Override
	public void upButtonPressed() {
		((RuleInterceptorTabComposite)getParent()).moveUpSelectedInterceptor();
	}
	
	@Override
	protected boolean isEditSupported() {
		return false;
	}
}
