package com.predic8.plugin.membrane.components;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

public class GridPanel extends Composite {

	public GridPanel(Composite parent, int margin) {
		super(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginTop = margin;
		layout.marginLeft = margin;
		layout.marginBottom = margin;
		layout.marginRight = margin;
		setLayout(layout);
	}

}
