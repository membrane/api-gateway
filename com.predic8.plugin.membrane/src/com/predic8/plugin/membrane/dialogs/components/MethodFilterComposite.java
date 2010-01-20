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
