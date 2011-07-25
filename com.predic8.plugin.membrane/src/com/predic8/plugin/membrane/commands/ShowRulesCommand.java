/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.plugin.membrane.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import com.predic8.membrane.core.Router;
import com.predic8.plugin.membrane.PluginUtil;
import com.predic8.plugin.membrane.views.RulesView;


public class ShowRulesCommand extends AbstractHandler {

	public Object execute(ExecutionEvent event) throws ExecutionException {
		RulesView rulesView = (RulesView)PluginUtil.showView(RulesView.VIEW_ID);
		rulesView.setInputForTable(Router.getInstance().getRuleManager());
		
		return null;
	}
	
}
