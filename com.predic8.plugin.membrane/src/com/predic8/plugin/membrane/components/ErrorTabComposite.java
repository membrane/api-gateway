package com.predic8.plugin.membrane.components;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TabFolder;

import com.predic8.membrane.core.http.Message;
import com.predic8.plugin.membrane.listeners.HighligtingLineStyleListner;

public class ErrorTabComposite extends AbstractTabComposite {

	public static final String TAB_TITLE = "ERROR";
	
	protected StyledText errorText;	
	
	public ErrorTabComposite(TabFolder parent) {
		super(parent, TAB_TITLE);
		errorText = new StyledText(this, SWT.BORDER | SWT.BEGINNING | SWT.H_SCROLL | SWT.MULTI | SWT.V_SCROLL);

		errorText.setFont(new Font(Display.getCurrent(), "Courier", 10, SWT.NORMAL));
		errorText.addLineStyleListener(new HighligtingLineStyleListner());
	}

	@Override
	public void update(Message msg) {
		if (msg == null)
			return;
		errorText.setText(msg.getErrorMessage());
		errorText.redraw();
		layout();
	}
}
