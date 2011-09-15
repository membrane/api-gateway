package com.predic8.plugin.membrane.dialogs.rule.composites;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;

import com.predic8.plugin.membrane.util.SWTUtil;

public class ProxyFeaturesTabComposite extends Composite {

	private StyledText text;

	public ProxyFeaturesTabComposite(final Composite parent) {
		super(parent, SWT.NONE);
		setLayout(SWTUtil.createGridLayout(1, 10));
		text = createStyledText();
	}

	private StyledText createStyledText() {
		StyledText text = new StyledText(this, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
		GridData gData = new GridData();
		gData.horizontalAlignment = GridData.FILL;
		gData.grabExcessHorizontalSpace = true;
		gData.verticalAlignment = GridData.FILL;
		gData.grabExcessVerticalSpace = true;
		text.setLayoutData(gData);
		return text;
	}

	public String getContent() {
		return text.getText();
	}
	
}
