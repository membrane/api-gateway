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
package com.predic8.plugin.membrane.dialogs.components;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.predic8.membrane.core.http.Request;
import com.predic8.plugin.membrane.filtering.ExchangesFilter;

public class MethodFilterComposite extends AbstractFilterComposite {

	public MethodFilterComposite(Composite parent, ExchangesFilter aFilter) {
		super(parent, aFilter);
	}
	
	private Button createMethodButton(Composite methodsComposite, String method) {
		Button bt = new Button(methodsComposite, SWT.CHECK);
		bt.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		bt.setText(method);
		bt.setData(method);
		bt.addSelectionListener(new MethodSelectionAdapter(bt, filter));
		return bt;
	}

	@Override
	protected String getGroupText() {
		return "Show Methods";
	}

	@Override
	protected String getShowAllText() {
		return "Display exchanges with any method";
	}

	@Override
	protected String getShowSelectedOnlyText() {
		return "Display exchanges with selected methods only";
	}

	@Override
	protected void initializeButtons(Composite composite) {
		buttons.add(createMethodButton(composite, Request.METHOD_GET));
		buttons.add(createMethodButton(composite, Request.METHOD_POST));
		buttons.add(createMethodButton(composite, Request.METHOD_PUT));
		buttons.add(createMethodButton(composite, Request.METHOD_DELETE));
		buttons.add(createMethodButton(composite, Request.METHOD_HEAD));
		buttons.add(createMethodButton(composite, Request.METHOD_TRACE));
	}

	@Override
	public String getFilterName() {
		return "Method";
	}
	
}
