package com.predic8.plugin.membrane.components.composites;

import org.eclipse.swt.SWT;

import com.predic8.plugin.membrane.dialogs.rule.composites.ProxyInterceptorTabComposite;

public class RuleInterceptorControlsComposite extends ControlsComposite {
	
	
	public RuleInterceptorControlsComposite(ProxyInterceptorTabComposite parent) {
		super(parent, SWT.NONE);
	}

	@Override
	public void downButtonPressed() {
		((ProxyInterceptorTabComposite)getParent()).moveDownSelectedInterceptor();
	}

	@Override
	public void editButtonPressed() {
		((ProxyInterceptorTabComposite)getParent()).editSelectedInterceptor();
	}

	@Override
	public void newButtonPressed() {
		((ProxyInterceptorTabComposite)getParent()).addNewInterceptor();
	}

	@Override
	public void removeButtonPressed() {
		((ProxyInterceptorTabComposite)getParent()).removeSelectedInterceptor();
	}

	@Override
	public void upButtonPressed() {
		((ProxyInterceptorTabComposite)getParent()).moveUpSelectedInterceptor();
	}
	
	@Override
	protected boolean isEditSupported() {
		return false;
	}
}
