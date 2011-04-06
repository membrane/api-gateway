/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */


package com.predic8.plugin.membrane.dialogs.rule;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;

import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleActionsTabComposite;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleGeneralInfoTabComposite;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleInterceptorTabComposite;

public abstract class RuleEditDialog extends Dialog {

	protected Rule rule;
	
	protected TabFolder tabFolder;
	
	protected RuleGeneralInfoTabComposite generalInfoComposite;
	
	protected RuleActionsTabComposite actionsComposite;
	
	protected RuleInterceptorTabComposite interceptorsComposite;
	
	protected RuleEditDialog(Shell parentShell) {
		super(parentShell);
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(getTitle());
		shell.setSize(520, 500);
	}
	
	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, "OK", false);
		createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", true);
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);
		GridLayout gridLayout = new GridLayout();
		gridLayout.marginTop = 10;
		gridLayout.marginLeft = 10;
		gridLayout.marginBottom = 10;
		gridLayout.marginRight = 10;
		
		container.setLayout(gridLayout);
		
		tabFolder = new TabFolder(container, SWT.NONE);
		GridData griddata4TabFolder = new GridData();
		griddata4TabFolder.widthHint = 440;
		griddata4TabFolder.heightHint = 440;
		griddata4TabFolder.grabExcessHorizontalSpace = true;
		griddata4TabFolder.grabExcessVerticalSpace = true;
		tabFolder.setLayoutData(griddata4TabFolder);
		
		
		return container;
	}
	
	public abstract String getTitle();
	
	public void setInput(Rule rule) {
		if (rule == null)
			return;
		this.rule = rule;
		generalInfoComposite.setRule(rule);
		actionsComposite.setInput(rule);
		interceptorsComposite.setInput(rule);
	}
	
	protected void openErrorDialog(String msg) {
		MessageDialog.openError(this.getShell(), "Error", msg);
	}

	protected void openWarningDialog(String msg) {
		MessageDialog.openWarning(this.getShell(), "Warning", msg);
	}

	protected boolean openConfirmDialog(String msg) {
		return MessageDialog.openConfirm(this.getShell(), "Confirm", msg);
	}
	
	public abstract void onOkPressed();
	
	@Override
	protected void okPressed() {
		onOkPressed();
		close();
	}
}
