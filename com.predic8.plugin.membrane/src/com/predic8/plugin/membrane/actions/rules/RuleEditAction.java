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

package com.predic8.plugin.membrane.actions.rules;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;

import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.dialogs.rule.ForwardingRuleEditDialog;
import com.predic8.plugin.membrane.dialogs.rule.ProxyRuleEditDialog;
import com.predic8.plugin.membrane.dialogs.rule.RuleEditDialog;

public class RuleEditAction extends Action {

	private StructuredViewer structuredViewer;

	public RuleEditAction(StructuredViewer viewer) {
		super();
		this.structuredViewer = viewer;
		setText("Edit Rule");
		setId("Rule Edit Action");
	}

	@Override
	public void run() {
		IStructuredSelection selection = (IStructuredSelection) structuredViewer.getSelection();
		Object selectedItem = selection.getFirstElement();

		try {
			if (selectedItem instanceof ForwardingRule) {
				openRuleDialog(new ForwardingRuleEditDialog(structuredViewer.getControl().getShell()), (ForwardingRule) selectedItem);

			} else if (selectedItem instanceof ProxyRule) {
				openRuleDialog(new ProxyRuleEditDialog(structuredViewer.getControl().getShell()), (ProxyRule) selectedItem);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void openRuleDialog(RuleEditDialog dialog, Rule rule) {
		if (dialog.getShell() == null) {
			dialog.create();
		}
		dialog.setInput(rule);
		dialog.open();
	}
	
}
