package com.predic8.plugin.membrane.views.util;

import org.eclipse.swt.widgets.TableColumn;

public class ShrinkThread extends MoveThread {

	public ShrinkThread(int width, TableColumn column) {
		super(width, column);
	}
	
	protected void process() {
		column.getDisplay().syncExec(new Runnable() {
			public void run() {
				column.setData("restoredWidth", new Integer(width));
			}
		});

		for (int i = width; i >= 0; i--) {
			setWidthForColumn(i);
		}
	}
}
