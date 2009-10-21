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


import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.predic8.plugin.membrane.viewers.ForwardingRuleViewer;

public class EditForwardingRuleDialog extends AbstractRuleDialog {

	public EditForwardingRuleDialog(Shell parentShell) {
		super(parentShell, "Edit Forwarding Rule");
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);
		container.setLayout(new GridLayout());
		
		ruleOptionalViewer = new ForwardingRuleViewer(container, SWT.NONE);
		ruleOptionalViewer.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		return container;
	}

}
