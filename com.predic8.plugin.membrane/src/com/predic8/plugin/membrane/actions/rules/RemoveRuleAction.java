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

package com.predic8.plugin.membrane.actions.rules;

import java.io.IOException;

import org.eclipse.jface.action.Action;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class RemoveRuleAction extends Action {

	private Rule selectedRule;

	public RemoveRuleAction() {
		setText("Remove Rule");
		setId("Remove Rule Action");
	}

	@Override
	public void run() {
		Router.getInstance().getRuleManager().removeRule(selectedRule);
		if (!Router.getInstance().getRuleManager().isAnyRuleWithPort(selectedRule.getKey().getPort())) {
			try {
				((HttpTransport) Router.getInstance().getTransport()).closePort(selectedRule.getKey().getPort());
			} catch (IOException e2) {
				e2.printStackTrace();
			}
		}
	}

	public void setSelectedRule(Rule selectedRule) {
		this.selectedRule = selectedRule;
	}
	
}
