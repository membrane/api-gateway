package com.predic8.plugin.membrane.util;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.Composite;

public class SWTUtil {

	
	public static GridLayout createGridLayout(int col, int margin) {
		GridLayout layout = new GridLayout();
		layout.numColumns = col;
		layout.marginTop = margin;
		layout.marginLeft = margin;
		layout.marginBottom = margin;
		layout.marginRight = margin;
		return layout;
	}
	
	public static GridData getGreedyGridData() {
		GridData gData = new GridData();
		gData.horizontalAlignment = GridData.FILL;
		gData.grabExcessHorizontalSpace = true;
		gData.verticalAlignment = GridData.FILL;
		gData.grabExcessVerticalSpace = true;
		return gData;
	}
	
	public static GridLayout createGridLayout(int col, int margin, int vSpacing) {
		GridLayout layout = new GridLayout();
		layout.numColumns = col;
		layout.marginTop = margin;
		layout.marginLeft = margin;
		layout.marginBottom = margin;
		layout.marginRight = margin;
		layout.verticalSpacing = vSpacing;
		return layout;
	}
	
	public static GridLayout createGridLayout(int col,  int marginTop, int marginLeft, int marginBottom, int marginRight) {
		GridLayout layout = new GridLayout();
		layout.numColumns = col;
		layout.marginTop = marginTop;
		layout.marginLeft = marginLeft;
		layout.marginBottom = marginBottom;
		layout.marginRight = marginRight;
		return layout;
	}
	
	public static Composite createGridComposite(Composite parent, int col, int margin) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(createGridLayout(col, margin));
		return composite;
	}
	
	
}
