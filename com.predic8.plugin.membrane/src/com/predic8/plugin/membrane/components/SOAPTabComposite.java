package com.predic8.plugin.membrane.components;

import java.io.ByteArrayInputStream;

import org.eclipse.swt.widgets.TabFolder;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.util.TextUtil;

public class SOAPTabComposite extends BodyTextTabComposite {

	public static final String TAB_TITLE = "SOAP";
	
	public SOAPTabComposite(TabFolder parent) {
		super(parent, TAB_TITLE);
	}

	public String getBodyText() {
		return bodyText.getText();
	}

	public void setBodyTextEditable(boolean bool) {
		bodyText.setEditable(bool);
	}

	public void setTabTitle(String tabName) {
		tabItem.setText(tabName);
	}

	public void setBodyText(String string) {
		if (string == null)
			return;
		bodyText.setText(string);
	}
	
	public void beautify(byte[] content) {
		if (content == null)
			return;
		ByteArrayInputStream bis = new ByteArrayInputStream(content);
		try {
			bodyText.setText(TextUtil.formatXML(bis));
		} catch (Exception ex) {
			bodyText.setText(new String(content));
		}
		bodyText.redraw();
	}
	
	protected boolean isBeautifyBody() {
		return Router.getInstance().getConfigurationManager().getConfiguration().getIndentMessage();
	}
	
	@Override
	public boolean isFormatSupported() {
		return true;
	}
}
