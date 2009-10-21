package com.predic8.rcp.membrane.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;

import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.resources.ImageKeys;
import com.predic8.plugin.membrane.views.BrowserView;

public class WebPredic8Action extends Action {

	private IWorkbenchWindow window;
	
	public WebPredic8Action(IWorkbenchWindow window) {
		super();
		this.window = window;
		setText("predic8 in web");
		setId("predic8 in web action");
		setImageDescriptor(MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_WORLD_GO));
	}
	
	@Override
	public void run() {
		IWorkbenchPage page = window.getActivePage();
		try {
			page.showView(BrowserView.VIEW_ID);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
}
