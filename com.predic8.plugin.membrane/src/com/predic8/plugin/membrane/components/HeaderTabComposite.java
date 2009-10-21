package com.predic8.plugin.membrane.components;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.TabFolder;

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
import com.predic8.plugin.membrane.viewers.HeaderTableViewer;

public class HeaderTabComposite extends AbstractTabComposite {

	protected HeaderTableViewer headerTableViewer;
	
	public HeaderTabComposite(TabFolder parent) {
		super(parent, "HTTP");
		headerTableViewer = new HeaderTableViewer(this, SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
	}

	@Override
	public void update(Message msg) {
		headerTableViewer.setInput(msg);
	}
	
	public void setWidgetEditable(boolean status) {
		headerTableViewer.setEditable(status);
	}
	
	public void updateWidget(Message msg, byte[] inputBodyBytes) {
		headerTableViewer.update(msg.getHeader().setValue(Header.CONTENT_LENGTH, Integer.toString(inputBodyBytes.length)), null);
	}
	
}
