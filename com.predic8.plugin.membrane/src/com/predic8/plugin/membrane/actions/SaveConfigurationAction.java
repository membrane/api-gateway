package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

import com.predic8.membrane.core.Router;

public class SaveConfigurationAction implements IWorkbenchWindowActionDelegate {

	private IWorkbenchWindow window;
	
	public SaveConfigurationAction() {
		
	}
	
	public void dispose() {

	}
	
	public void init(IWorkbenchWindow window) {
		this.window = window;
	}

	public void run(IAction action) {
		FileDialog fd = new FileDialog(window.getShell(), SWT.SAVE);
		fd.setText("Save Configuration");
		fd.setFilterPath("C:/");
        String[] filterExt = { "*.xml"};
        fd.setFilterExtensions(filterExt);
        String selected = fd.open();
        if (selected != null && !selected.equals("")) {
        	Router.getInstance().getConfigurationManager().saveConfiguration(selected);
        }
	}

	public void selectionChanged(IAction action, ISelection selection) {

	}

}
