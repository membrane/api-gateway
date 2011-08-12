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

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class RemoveProxyAction extends AbstractProxyAction {

	public RemoveProxyAction() {
		super("Remove Rule Action", "Remove Rule");
	}

	@Override
	public void run() {
		getRuleManager().removeRule(selectedRule);
		if (getRuleManager().isAnyRuleWithPort(selectedRule.getKey().getPort()))
			return;

		try {
			getHttpTransport().closePort(selectedRule.getKey().getPort());
		} catch (IOException e2) {
			e2.printStackTrace();
		}
	}

	private RuleManager getRuleManager() {
		return Router.getInstance().getRuleManager();
	}

	private HttpTransport getHttpTransport() {
		return ((HttpTransport) Router.getInstance().getTransport());
	}

}
