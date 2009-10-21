package com.predic8.plugin.membrane.components;

import org.eclipse.swt.widgets.TabFolder;

import com.predic8.membrane.core.http.Message;

public abstract class BodyTabComposite extends AbstractTabComposite {

	protected boolean bodyModified;
	
	public BodyTabComposite(TabFolder parent) {
		super(parent);
	}
	
	public BodyTabComposite(TabFolder parent, String tabTitle) {
		super(parent, tabTitle);
	
	}

	public boolean isBodyModified() {
		return bodyModified;
	}
	
	public void setBodyModified(boolean status) {
		bodyModified = status;
	}
	
	public String getBodyText() {
		return "";
	}

	public void setBodyTextEditable(boolean bool) {
		
	}

	public void setBodyText(String string) {
	
	}
	
	public void beautify(Message msg) {
		
	}
	
	public boolean isFormatSupported() {
		return false;
	}
	
	public boolean isSaveSupported() {
		return true;
	}
	
}
