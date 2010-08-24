package com.predic8.plugin.membrane.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import com.predic8.membrane.core.Router;
import com.predic8.plugin.membrane.views.RuleStatisticsView;


public class ShowRuleStatisticsCommand extends AbstractHandler {

	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
		
		IWorkbenchPage page = window.getActivePage();
		try {
			page.showView(RuleStatisticsView.VIEW_ID);
			RuleStatisticsView ruleStatisticsView = (RuleStatisticsView)window.getActivePage().findView(RuleStatisticsView.VIEW_ID);
			ruleStatisticsView.setInputForTable(Router.getInstance().getRuleManager());
			
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
		return null;
	}

}
