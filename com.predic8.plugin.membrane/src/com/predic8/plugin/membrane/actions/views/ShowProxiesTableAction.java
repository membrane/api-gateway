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

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

import com.predic8.membrane.core.Router;
import com.predic8.plugin.membrane.PluginUtil;
import com.predic8.plugin.membrane.views.ProxyTableView;

public class ShowProxiesTableAction implements IWorkbenchWindowActionDelegate {

	private IWorkbenchWindow window;

	public ShowProxiesTableAction() {

	}

	public void dispose() {

	}

	public void init(IWorkbenchWindow window) {
		this.window = window;
	}

	public void run(IAction action) {
		ProxyTableView proxyTableView = (ProxyTableView) PluginUtil.showView(ProxyTableView.VIEW_ID);
		proxyTableView.getTableViewer().setInput(Router.getInstance().getRuleManager());

	}

	public void selectionChanged(IAction action, ISelection selection) {

	}

}
