package com.predic8.plugin.membrane.components;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TabFolder;

import com.predic8.membrane.core.http.Message;

public class RawTabComposite extends AbstractTabComposite {

	public static final String TAB_TITLE = "Raw";
	
	private StyledText rawText;

	public RawTabComposite(TabFolder parent) {
		super(parent, TAB_TITLE);
		
		rawText = new StyledText(this, SWT.BORDER | SWT.BEGINNING | SWT.H_SCROLL | SWT.MULTI | SWT.V_SCROLL);
		rawText.setEditable(false);
		rawText.setFont(new Font(Display.getCurrent(), "Courier", 10, SWT.NORMAL));
	}

	@Override
	public void update(Message msg) {
		rawText.setText(msg.toString());
		layout();
	}
}
