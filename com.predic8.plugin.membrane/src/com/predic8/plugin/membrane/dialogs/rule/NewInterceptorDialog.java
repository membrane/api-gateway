package com.predic8.plugin.membrane.dialogs.rule;

import org.eclipse.swt.widgets.Shell;

import com.predic8.plugin.membrane.dialogs.rule.composites.RuleInterceptorTabComposite;

public class NewInterceptorDialog extends InterceptorDialog {

	public NewInterceptorDialog(Shell parentShell, RuleInterceptorTabComposite parent) {
		super(parentShell, parent);
	}

	@Override
	public String getDialogTitle() {
		return "New Interceptor";
	}

}
