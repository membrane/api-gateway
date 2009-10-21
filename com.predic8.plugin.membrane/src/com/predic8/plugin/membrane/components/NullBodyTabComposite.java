package com.predic8.plugin.membrane.components;

import org.eclipse.swt.widgets.TabFolder;

public class NullBodyTabComposite extends BodyTabComposite {

	public static final String TAB_TITLE = "";
	
	public NullBodyTabComposite(TabFolder parent) {
		super(parent);
		setTabTitle(TAB_TITLE);
	}

}
