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

import java.io.IOException;

import org.eclipse.swt.widgets.TabFolder;

import com.predic8.membrane.core.Router;
import com.predic8.plugin.membrane.beautifier.JSONBeautifier;
import com.predic8.plugin.membrane.listeners.JSONHighlitingStylelistener;


public class JSONTabComposite extends BodyTextTabComposite {

	public static final String TAB_TITLE = "JSON";
	
	private JSONBeautifier beautifier;
	
	public JSONTabComposite(TabFolder parent) {
		super(parent, TAB_TITLE);
		bodyText.addLineStyleListener(new JSONHighlitingStylelistener());
		beautifier = Router.getInstance().getBean("JSONBeautifier", JSONBeautifier.class);
	}

	@Override
	public void beautify(byte[] content, String encoding) throws IOException {
	    bodyText.setText(beautifier.beautify(content));
	    bodyText.redraw();
	}
	
	@Override
	protected boolean isBeautifyBody() {
		return true;
	}
	
	public void setBeautifier(JSONBeautifier beautifier) {
		this.beautifier = beautifier;
	}
}
