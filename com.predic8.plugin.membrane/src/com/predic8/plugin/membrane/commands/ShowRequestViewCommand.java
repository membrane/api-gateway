package com.predic8.plugin.membrane.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import com.predic8.plugin.membrane.PluginUtil;
import com.predic8.plugin.membrane.views.RequestView;


public class ShowRequestViewCommand extends AbstractHandler {

	public Object execute(ExecutionEvent event) throws ExecutionException {
		PluginUtil.showView(RequestView.VIEW_ID);
		return null;
	}

}
