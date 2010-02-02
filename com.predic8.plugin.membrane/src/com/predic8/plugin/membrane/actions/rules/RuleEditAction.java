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
import com.predic8.plugin.membrane.dialogs.rule.ForwardingRuleEditDialog;
import com.predic8.plugin.membrane.dialogs.rule.ProxyRuleEditDialog;

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
				ForwardingRule selectedRule = (ForwardingRule) selectedItem;
				ForwardingRuleEditDialog dialog = new ForwardingRuleEditDialog(structuredViewer.getControl().getShell());
				if (dialog.getShell() == null) {
					dialog.create();
				}
				dialog.setInput(selectedRule);
				dialog.open();

				// EditForwardingRuleDialog dialog = new
				// EditForwardingRuleDialog(structuredViewer.getControl().getShell());
				// if(dialog.getShell()==null) {
				// dialog.create();
				// }
				// dialog.resetValueForRuleOptionsViewer(selectedRule);
				// dialog.open();
			} else if (selectedItem instanceof ProxyRule) {
				ProxyRule selectedRule = (ProxyRule) selectedItem;
				ProxyRuleEditDialog dialog = new ProxyRuleEditDialog(structuredViewer.getControl().getShell());
				if (dialog.getShell() == null) {
					dialog.create();
				}
				dialog.setInput(selectedRule);
				dialog.open();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

}
