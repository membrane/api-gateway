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

package com.predic8.plugin.membrane.actions.views;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.views.RuleDetailsView;

public class ShowRuleDetailsViewAction extends Action {

	private StructuredViewer structuredViewer;

	public ShowRuleDetailsViewAction(StructuredViewer viewer) {
		this.structuredViewer = viewer;
		setText("Show Rule Details");
		setId("Show Rule Details Action");
	}

	public void run() {

		IStructuredSelection selection = (IStructuredSelection) structuredViewer.getSelection();
		Object selectedItem = selection.getFirstElement();

		if (selectedItem instanceof Rule) {
			try {
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				page.showView(RuleDetailsView.VIEW_ID);
				RuleDetailsView ruleView =  (RuleDetailsView)page.findView(RuleDetailsView.VIEW_ID);
				ruleView.setRuleToDisplay((Rule) selectedItem);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

	}

}
