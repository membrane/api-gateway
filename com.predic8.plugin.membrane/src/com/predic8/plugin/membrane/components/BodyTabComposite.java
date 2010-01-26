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

package com.predic8.plugin.membrane.components;

import org.eclipse.swt.widgets.TabFolder;

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
	
	public void beautify(byte[] content) {
		
	}
	
	public boolean isFormatSupported() {
		return false;
	}
	
	public boolean isSaveSupported() {
		return true;
	}
	
}
