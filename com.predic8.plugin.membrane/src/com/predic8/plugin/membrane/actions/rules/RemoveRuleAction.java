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

import java.io.IOException;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class RemoveRuleAction extends Action {

	private StructuredViewer structuredViewer;

	public RemoveRuleAction(StructuredViewer viewer) {
		this.structuredViewer = viewer;
		setText("Remove Rule");
		setId("Remove Rule Action");
	}

	@Override
	public void run() {
		IStructuredSelection selection = (IStructuredSelection) structuredViewer.getSelection();
		Object selectedItem = selection.getFirstElement();
		
		if (selectedItem instanceof Rule) {
			Rule rule = (Rule) selectedItem;
			Router.getInstance().getRuleManager().removeRule(rule);
			structuredViewer.setSelection(null);
			if (!Router.getInstance().getRuleManager().isAnyRuleWithPort(rule.getKey().getPort())) {
				try {
					((HttpTransport) Router.getInstance().getTransport()).closePort(rule.getKey().getPort());
				} catch (IOException e2) {
					e2.printStackTrace();
				}
			}
		} 
	}
	
}
