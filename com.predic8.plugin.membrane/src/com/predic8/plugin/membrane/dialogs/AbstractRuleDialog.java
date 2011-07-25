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

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;

import com.predic8.membrane.core.rules.Rule;

public abstract class AbstractRuleDialog extends Dialog {

	protected AbstractRuleViewer ruleOptionalViewer;
	
	protected String dialogTitle;
	
	protected TabFolder tabFolder; 
	
	public AbstractRuleDialog(Shell parentShell, String title) {
		super(parentShell);
		this.dialogTitle = title;
	}
	
	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(dialogTitle);
		shell.setSize(400, 440);
	}

	
	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, "OK", false);
		createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", true);
	}

	public void resetValueForRuleOptionsViewer(Rule selectedRule) {
		if (ruleOptionalViewer != null) {
			ruleOptionalViewer.resetValues(selectedRule);
		}
	}

	@Override
	protected void okPressed() {
		ruleOptionalViewer.editSelectedRule();
		close();
	}
	
}
