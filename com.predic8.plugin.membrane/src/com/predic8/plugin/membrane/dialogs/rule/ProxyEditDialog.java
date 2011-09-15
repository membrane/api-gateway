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

package com.predic8.plugin.membrane.dialogs.rule;

import javax.xml.stream.XMLStreamReader;

import org.eclipse.swt.widgets.*;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.*;
import com.predic8.plugin.membrane.dialogs.rule.composites.ProxyRuleKeyTabComposite;

public class ProxyEditDialog extends AbstractProxyEditDialog {

	private ProxyRuleKeyTabComposite ruleKeyComposite;

	public ProxyEditDialog(Shell parentShell) {
		super(parentShell);
	}

	@Override
	public String getTitle() {
		return "Edit Proxy";
	}

	@Override
	protected void createRuleKeyComposite() {
		ruleKeyComposite = new ProxyRuleKeyTabComposite(tabFolder);
	}
	
	@Override
	protected Composite getRuleKeyComposite() {
		return ruleKeyComposite;
	}
	
	@Override
	public void setInput(Rule rule) {
		super.setInput(rule);
		//ruleKeyComposite.setInput(rule.getKey());
	}

	@Override
	public void onOkPressed() {
		int port = 0;
		try {
			port = Integer.parseInt(ruleKeyComposite.getListenPort());
		} catch (NumberFormatException nfe) {
			openErrorDialog("Illeagal input! Please check listen port again");
			return;
		}

		ProxyRuleKey ruleKey = new ProxyRuleKey(port);
		doRuleUpdate(ruleKey);
		
	}	
	
	@Override
	protected Rule parseRule(XMLStreamReader reader) throws Exception {
		return (ProxyRule)new ProxyRule(Router.getInstance()).parse(reader);
	}

}
