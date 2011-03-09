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

package com.predic8.plugin.membrane.dialogs.rule;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleInterceptorTabComposite;

public class EditInterceptorDialog extends InterceptorDialog {

	public EditInterceptorDialog(Shell parentShell, RuleInterceptorTabComposite parent) {
		super(parentShell, parent);
	}

	@Override
	public String getDialogTitle() {
		return "Edit Interceptor";
	}
	
	public void setInput(final Interceptor interceptor) {
		if (interceptor == null)
			return;
		Display.getCurrent().asyncExec(new Runnable() {
			public void run() {
				textName.setText(interceptor.getDisplayName());
				textClassName.setText(interceptor.getClass().getName());
			}
		});
	}

}
