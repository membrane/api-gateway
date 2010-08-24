package com.predic8.plugin.membrane.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;

import com.predic8.membrane.core.Router;


public class SaveConfigurationCommand extends AbstractHandler {

	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		FileDialog fd = new FileDialog(Display.getDefault().getActiveShell(), SWT.SAVE);
		fd.setText("Save Configuration");
		fd.setFilterPath("C:/");
        String[] filterExt = { "*.xml"};
        fd.setFilterExtensions(filterExt);
        String selected = fd.open();
        if (selected != null && !selected.equals("")) {
        	try {
				Router.getInstance().getConfigurationManager().saveConfiguration(selected);
			} catch (Exception e) {
				e.printStackTrace();
				MessageDialog.openError(Display.getCurrent().getActiveShell(), "Error", "Unable to save configuration: " + e.getMessage());
			}
        }
		
		return null;
	}

}
