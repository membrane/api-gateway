package com.predic8.plugin.membrane.dialogs.components;

import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;

import com.predic8.plugin.membrane.filtering.MethodFilter;

public class MethodSelectionAdapter extends SelectionAdapter {

	private Button bt;
	
	private MethodFilter filter;
	
	public MethodSelectionAdapter(Button bt, MethodFilter filter) {
		this.bt = bt;
		this.filter = filter;
	}
	
	@Override
	public void widgetSelected(SelectionEvent e) {
		if (bt.getSelection()) {
			filter.getDisplayedMethods().add((String) bt.getData());
		} else {
			filter.getDisplayedMethods().remove((String) bt.getData());
		}
	}
	
}
