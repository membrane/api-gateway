package com.predic8.plugin.membrane;

import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

public class PluginUtil {
	
	public static IViewPart showView(String viewId) {
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		try {
			page.showView(viewId);
			return page.findView(viewId);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Unable to find view. View ID may be not correct: " + viewId);
		}
	}
	
}
