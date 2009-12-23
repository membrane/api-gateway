package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.widgets.Display;

import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.dialogs.ExchangesTableSorterDialog;
import com.predic8.plugin.membrane.resources.ImageKeys;
import com.predic8.plugin.membrane.views.ExchangesView;

public class ShowSortersDialogAction extends Action {

	public static final String ID = "Show Table Sorters Action";
	
	private ExchangesView parentView;
	
	public ShowSortersDialogAction(ExchangesView exchangesView) {
		setText("Sorters");
		setId(ID);
		setImageDescriptor(MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_SORTER));
		this.parentView = exchangesView;
	}
	
	@Override
	public void run() {
		ExchangesTableSorterDialog dialog = new ExchangesTableSorterDialog(Display.getCurrent().getActiveShell(), parentView);
		if(dialog.getShell()==null) {
			dialog.create();
		}
		dialog.open();
	}
	
}
