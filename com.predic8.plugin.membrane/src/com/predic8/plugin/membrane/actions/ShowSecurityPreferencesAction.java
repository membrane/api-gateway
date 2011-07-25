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
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.dialogs.PreferencesUtil;

import com.predic8.plugin.membrane.preferences.SecurityPreferencePage;

public class ShowSecurityPreferencesAction extends Action {

	public static final String ID = "Show Security Preferences Page Action";
	

	public ShowSecurityPreferencesAction() {
		setText("Show Security Preferences");
		setId(ID);
	}
	
	@Override
	public void run() {		
		PreferenceDialog dlg = PreferencesUtil.createPreferenceDialogOn(Display.getCurrent().getActiveShell(), SecurityPreferencePage.PAGE_ID, null, null);
		dlg.open();
	}
	
}
