package com.predic8.plugin.membrane.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import com.predic8.membrane.core.Router;
import com.predic8.plugin.membrane.PluginUtil;
import com.predic8.plugin.membrane.views.RuleStatisticsView;

public class ShowRuleStatisticsCommand extends AbstractHandler {

	public Object execute(ExecutionEvent event) throws ExecutionException {
		RuleStatisticsView ruleStatisticsView = (RuleStatisticsView) PluginUtil.showView(RuleStatisticsView.VIEW_ID);
		ruleStatisticsView.setInputForTable(Router.getInstance().getRuleManager());
		return null;
	}

}
