/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.plugin.membrane.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;

import com.predic8.membrane.core.Router;


public class LoadConfigurationCommand extends AbstractHandler {

	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		FileDialog filedialog = new FileDialog(Display.getDefault().getActiveShell(), SWT.OPEN);
		filedialog.setText("Load Configuration");
		filedialog.setFilterPath("C:/");
		String[] filterExt = { "*.xml" };
		filedialog.setFilterExtensions(filterExt);
		String selected = filedialog.open();

		if (selected == null || selected.equals(""))
			return null;

		try {
			Router.getInstance().getConfigurationManager().loadConfiguration(selected);
		} catch (Exception e) {
			MessageDialog.openError(Display.getDefault().getActiveShell(), "Load Configuration Error", e.getMessage());
		}

		
		return null;
	}

}
