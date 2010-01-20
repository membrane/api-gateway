package com.predic8.plugin.membrane.dialogs.components;

import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;

import com.predic8.plugin.membrane.filtering.ExchangesFilter;

public class MethodSelectionAdapter extends SelectionAdapter {

	private Button bt;
	
	private ExchangesFilter filter;
	
	public MethodSelectionAdapter(Button bt, ExchangesFilter filter) {
		this.bt = bt;
		this.filter = filter;
	}
	
	@Override
	public void widgetSelected(SelectionEvent e) {
		if (bt.getSelection()) {
			filter.getDisplayedItems().add((String) bt.getData());
		} else {
			filter.getDisplayedItems().remove((String) bt.getData());
		}
	}
	
}
