package com.predic8.plugin.membrane.views.util;

import org.eclipse.swt.widgets.TableColumn;

public class ExpandThread extends MoveThread {

	public ExpandThread(int width, TableColumn column) {
		super(width, column);
	}
	protected void process() {
		for (int i = 0; i <= width; i++) {
			setWidthForColumn(i);
		}
	}
	
}
