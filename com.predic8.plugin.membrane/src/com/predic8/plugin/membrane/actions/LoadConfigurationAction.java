package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

public class LoadConfigurationAction implements IWorkbenchWindowActionDelegate {

	
	private IWorkbenchWindow window;
	
	public LoadConfigurationAction() {
		
	}
	
	public void dispose() {

	}

	public void init(IWorkbenchWindow window) {
		this.window = window;
	}

	public void run(IAction action) {
		FileDialog filedialog = new FileDialog(window.getShell(), SWT.OPEN);
		filedialog.setText("Load Configuration");
		filedialog.setFilterPath("C:/");
		String[] filterExt = { "*.xml" };
		filedialog.setFilterExtensions(filterExt);
		String selected = filedialog.open();

		if (selected == null || selected.equals(""))
			return;

		try {
			//Core.getConfigurationManager().loadConfiguration(selected);
		} catch (Exception e) {
			MessageDialog.openError(window.getShell(), "Load Configuration Error", e.getMessage());
		}

	}

	public void selectionChanged(IAction action, ISelection selection) {

	}

}
