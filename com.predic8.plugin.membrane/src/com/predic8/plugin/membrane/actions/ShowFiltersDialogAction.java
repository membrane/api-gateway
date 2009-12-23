package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.widgets.Display;

import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.dialogs.ExchangesTableFilterDialog;
import com.predic8.plugin.membrane.resources.ImageKeys;
import com.predic8.plugin.membrane.views.ExchangesView;

public class ShowFiltersDialogAction extends Action {

	public static final String ID = "Show Table Filters Action";
	
	private ExchangesView parentView;
	
	public ShowFiltersDialogAction(ExchangesView exchangesView) {
		setText("Filters");
		setId(ID);
		setImageDescriptor(MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_FILTER));
		this.parentView = exchangesView;
	}
	
	@Override
	public void run() {
		ExchangesTableFilterDialog dialog = new ExchangesTableFilterDialog(Display.getCurrent().getActiveShell(), parentView);
		if(dialog.getShell()==null) {
			dialog.create();
		}
		dialog.open();
	}
	
}
