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
		bodyText.setText(string);
	}
	
	public void beautify(byte[] content) {
		bodyText.setText(TextUtil.formatXML(new ByteArrayInputStream(content)));
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
