/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.plugin.membrane.components.composites.tabmanager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import org.eclipse.swt.widgets.TabFolder;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.util.TextUtil;
import com.predic8.plugin.membrane.listeners.HighligtingLineStyleListner;

public class SOAPTabComposite extends BodyTextTabComposite {

	public static final String TAB_TITLE = "SOAP";
	
	public SOAPTabComposite(TabFolder parent) {
		super(parent, TAB_TITLE);
		bodyText.addLineStyleListener(new HighligtingLineStyleListner());
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
	
	public void beautify(byte[] content, String encoding) throws IOException {
		bodyText.setText(TextUtil.formatXML( new InputStreamReader(new ByteArrayInputStream(content), Constants.ENCODING_UTF_8)));
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
