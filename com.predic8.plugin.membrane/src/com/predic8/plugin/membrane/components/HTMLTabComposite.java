package com.predic8.plugin.membrane.components;

import org.eclipse.swt.widgets.TabFolder;

import com.predic8.membrane.core.http.Message;

public class HTMLTabComposite extends BodyTextTabComposite {

	public static final String TAB_TITLE = "HTML";
	
	public HTMLTabComposite(TabFolder parent) {
		super(parent, TAB_TITLE);
	}

	public void update(Message msg) {
		if (msg == null)
			return;
		setBodyText(new String(msg.getBody().getContent()));
	}
	
}
