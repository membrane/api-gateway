package com.predic8.plugin.membrane.util;

import org.eclipse.swt.layout.GridLayout;

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
	
}
