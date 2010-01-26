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

package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.widgets.Display;

import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.dialogs.ExchangesTableFilterDialog;
import com.predic8.plugin.membrane.resources.ImageKeys;
import com.predic8.plugin.membrane.views.ExchangesView;

public class ShowFiltersDialogAction extends Action {

	public static final String ID = "Show Table Filters Action";
	
	private ExchangesView parentView;
	
	public ShowFiltersDialogAction(ExchangesView exchangesView) {
		setText("Filters");
		setId(ID);
		setImageDescriptor(MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_FILTER));
		this.parentView = exchangesView;
	}
	
	@Override
	public void run() {
		ExchangesTableFilterDialog dialog = new ExchangesTableFilterDialog(Display.getCurrent().getActiveShell(), parentView);
		if(dialog.getShell()==null) {
			dialog.create();
		}
		dialog.open();
	}
	
}
