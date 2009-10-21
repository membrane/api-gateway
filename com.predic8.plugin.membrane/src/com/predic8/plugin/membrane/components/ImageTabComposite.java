package com.predic8.plugin.membrane.components;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TabFolder;

import com.predic8.membrane.core.http.Message;

public class ImageTabComposite extends BodyTabComposite {

	public static final String TAB_TITLE = "Image";
	
	protected Label imageLabel;
	
	public ImageTabComposite(TabFolder parent) {
		super(parent, TAB_TITLE);
		imageLabel = new Label(this, SWT.BORDER);
	}
	
	@Override
	public void update(Message msg) {
		if (msg == null)
			return;
		Image img = new Image(Display.getCurrent(), msg.getBodyAsStream());
		imageLabel.setImage(img);
	}

}
