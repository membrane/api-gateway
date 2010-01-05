package com.predic8.plugin.membrane.dialogs.rule;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleInterceptorTabComposite;

public class EditInterceptorDialog extends InterceptorDialog {

	public EditInterceptorDialog(Shell parentShell, RuleInterceptorTabComposite parent) {
		super(parentShell, parent);
	}

	@Override
	public String getDialogTitle() {
		return "Edit Interceptor";
	}
	
	public void setInput(final Interceptor interceptor) {
		if (interceptor == null)
			return;
		Display.getCurrent().asyncExec(new Runnable() {
			public void run() {
				textName.setText(interceptor.getDisplayName());
				textClassName.setText(interceptor.getClass().getName());
			}
		});
	}

}
