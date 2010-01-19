package com.predic8.plugin.membrane.dialogs;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;

public class AbstractExchangesTableDialog extends Dialog {

	String dialogTite;
	
	protected AbstractExchangesTableDialog(Shell parentShell, String title) {
		super(parentShell);
		
	}

	@Override
	protected void initializeBounds() {
		super.initializeBounds();
		Shell shell = this.getShell();
		Monitor primary = shell.getMonitor();
		Rectangle bounds = primary.getBounds();
		Rectangle rect = shell.getBounds();
		shell.setLocation(bounds.x + (bounds.width - rect.width) / 2, bounds.y + (bounds.height - rect.height) / 2);
	}
	
	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Exchange Sorters");
		shell.setSize(440, 560);
	}
	
}
