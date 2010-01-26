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

package com.predic8.plugin.membrane.dialogs;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.components.RuleOptionsActionsGroup;
import com.predic8.plugin.membrane.components.RuleOptionsBlockComp;

public abstract class AbstractRuleViewer extends Composite {

	protected Rule rule;
	
	protected Text nameText;
	
	protected Button ruleOptionsModifyButton;
	protected Button ruleOptionsResetButton;
	
	protected RuleOptionsBlockComp ruleOptionsBlockComp;
	
	protected RuleOptionsActionsGroup ruleOptionsActionsGroup;
	
	protected Composite ruleOptionsCommandComp;
	
	public AbstractRuleViewer(Composite parent) {
		super(parent, SWT.NONE);
		
		final GridLayout gridLayout4OptionsViewComp = new GridLayout();
		gridLayout4OptionsViewComp.numColumns = 1;
		setLayout(gridLayout4OptionsViewComp);

		final GridLayout gridLayout4NameComposite = new GridLayout();
		gridLayout4NameComposite.numColumns = 2;
		
		
		Composite nameComposite = new Composite(this, SWT.NONE);
		nameComposite.setLayout(gridLayout4NameComposite);
		nameComposite.setLayoutData(new GridData( GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));
		
		Label nameLabel = new Label(nameComposite, SWT.NONE);
		nameLabel.setText("Rule Name: ");
		
		nameText = new Text(nameComposite, SWT.BORDER);
		GridData gridData4NameText = new GridData();
		gridData4NameText.widthHint = 150;
		nameText.setLayoutData(gridData4NameText);
		
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
	
	public void setEnableOnlyModifyAndRestoreButton(boolean b) {
		ruleOptionsModifyButton.setEnabled(b);
		ruleOptionsResetButton.setEnabled(b);
	}
	
	public abstract void editSelectedRule();
	
	public abstract void resetValues(Rule selectedRule);
}
