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

package com.predic8.rcp.membrane.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.ui.IWorkbenchWindow;

import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.PluginUtil;
import com.predic8.plugin.membrane.resources.ImageKeys;
import com.predic8.plugin.membrane.views.BrowserView;

public class WebPredic8Action extends Action {

	private IWorkbenchWindow window;
	
	public WebPredic8Action(IWorkbenchWindow window) {
		super();
		this.window = window;
		setText("predic8 in web");
		setId("predic8 in web action");
		setImageDescriptor(MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_WORLD_GO));
	}
	
	@Override
	public void run() {
		PluginUtil.showView(BrowserView.VIEW_ID);
	}
	
}
