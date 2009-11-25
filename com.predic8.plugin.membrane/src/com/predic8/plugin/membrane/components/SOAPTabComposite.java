package com.predic8.plugin.membrane.components;

import org.eclipse.swt.widgets.TabFolder;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.http.Message;
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
	
	public void beautify(Message msg) {
		if (msg == null)
			return;
		try {
			bodyText.setText(TextUtil.formatXML(msg.getBodyAsStream()));
		} catch (Exception ex) {
			bodyText.setText(new String(msg.getBody().getContent()));
		}
		bodyText.redraw();
	}
	
	@Override
	public void update(Message msg) {
		if (msg == null)
			return;
		if (isBeautifyBody()) {
			this.beautify(msg);
		} else {
			this.setBodyText(new String(msg.getBody().getContent()));
		}
	}
	
	private boolean isBeautifyBody() {
		return Router.getInstance().getConfigurationManager().getConfiguration().getIndentMessage();
	}
	
	@Override
	public boolean isFormatSupported() {
		return true;
	}
}
