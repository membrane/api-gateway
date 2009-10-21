package com.predic8.plugin.membrane.dialogs;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.predic8.plugin.membrane.viewers.ProxyRuleViewer;

public class EditProxyRuleDialog extends AbstractRuleDialog {

	public EditProxyRuleDialog(Shell parentShell) {
		super(parentShell, "Edit Proxy Rule");
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);
		container.setLayout(new GridLayout());
		
		ruleOptionalViewer = new ProxyRuleViewer(container, SWT.NONE);
		ruleOptionalViewer.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		return container;
	}
	
}
