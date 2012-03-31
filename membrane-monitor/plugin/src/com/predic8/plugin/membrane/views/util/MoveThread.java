package com.predic8.plugin.membrane.views.util;

import org.eclipse.swt.widgets.TableColumn;

public abstract class MoveThread extends Thread {

	protected int width = 0;
	protected TableColumn column;
	
	public MoveThread(int width, TableColumn column) {
		this.width = width;
		this.column = column;
	}
	
	protected abstract void process();
	
	@Override
	public void run() {
		process();
	}
	
	protected void setWidthForColumn(int i) {
		final int index = i;
		column.getDisplay().syncExec(new Runnable() {
			public void run() {
				column.setWidth(index);
			}
		});
	}
	
}
