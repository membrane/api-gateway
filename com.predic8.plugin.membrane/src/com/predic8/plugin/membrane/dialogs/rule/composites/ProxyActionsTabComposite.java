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
package com.predic8.plugin.membrane.dialogs.rule.composites;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.components.GridPanel;

public class ProxyActionsTabComposite extends GridPanel {

	private Button btBlockRequest;

	private Button btBlockResponse;

	public ProxyActionsTabComposite(Composite parent) {
		super(parent, 20, 1);
		
		Group ruleActionGroup = new Group(this, SWT.NONE);
		ruleActionGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		ruleActionGroup.setLayout(new GridLayout());

		btBlockRequest = new Button(ruleActionGroup, SWT.CHECK);
		btBlockRequest.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {

			}
		});
		btBlockRequest.setText("Block Request");

		btBlockResponse = new Button(ruleActionGroup, SWT.CHECK);
		btBlockResponse.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {

			}
		});
		btBlockResponse.setText("Block Response");

	}

	public boolean isRequestBlocked() {
		return btBlockRequest.getSelection();
	}
	
	public boolean isResponseBlocked() {
		return btBlockResponse.getSelection();
	}
	
	public void setInput(Rule rule) {
		btBlockRequest.setSelection((rule.isBlockRequest()));
		btBlockResponse.setSelection(rule.isBlockResponse());
	}
	
}
