package com.predic8.plugin.membrane.labelproviders;

import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

public class TableHeaderLabelProvider extends LabelProvider implements IColorProvider {

	public Color getBackground(Object element) {
		return Display.getDefault().getSystemColor(SWT.COLOR_MAGENTA);
	}

	public Color getForeground(Object element) {
		return Display.getDefault().getSystemColor(SWT.COLOR_BLUE);
	}

}
