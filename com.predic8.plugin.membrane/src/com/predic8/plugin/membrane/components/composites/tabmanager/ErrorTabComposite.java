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

package com.predic8.plugin.membrane.components.composites.tabmanager;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TabFolder;

import com.predic8.membrane.core.http.Message;
import com.predic8.plugin.membrane.listeners.HighligtingLineStyleListner;

public class ErrorTabComposite extends AbstractTabComposite {

	public static final String TAB_TITLE = "ERROR";
	
	protected StyledText errorText;	
	
	public ErrorTabComposite(TabFolder parent) {
		super(parent, TAB_TITLE);
		errorText = new StyledText(this, SWT.BORDER | SWT.BEGINNING | SWT.H_SCROLL | SWT.MULTI | SWT.V_SCROLL);

		errorText.setFont(new Font(Display.getCurrent(), "Courier", 10, SWT.NORMAL));
		errorText.addLineStyleListener(new HighligtingLineStyleListner());
	}

	@Override
	public void updateInternal(Message msg) {
		errorText.setText(msg.getErrorMessage());
		errorText.redraw();
		layout();
	}
}
