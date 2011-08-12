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

package com.predic8.plugin.membrane.views;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.components.ProxyDetailsComposite;

public class ProxyDetailsView extends ViewPart {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.ProxyDetailsView";
	
	private ProxyDetailsComposite proxyDetailsComposite;
	
	@Override
	public void createPartControl(Composite parent) {
		proxyDetailsComposite = new ProxyDetailsComposite(parent);
	}

	@Override
	public void setFocus() {
		proxyDetailsComposite.setFocus();
	}

	public void setProxyToDisplay(Rule rule) {
		proxyDetailsComposite.configure(rule);
		proxyDetailsComposite.layout();
	}
	
}
