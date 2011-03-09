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
