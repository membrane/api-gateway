package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.Action;

import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.resources.ImageKeys;
import com.predic8.plugin.membrane.views.ExchangesView;

public class ShowSortersDialogAction extends Action {

	private ExchangesView parentView;
	
	public ShowSortersDialogAction(ExchangesView exchangesView) {
		setText("Sorters");
		setId("Show Table Sorters Action");
		setImageDescriptor(MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_SORTER));
		this.parentView = exchangesView;
	}
	
	@Override
	public void run() {
		if (parentView != null) {
			
		}
	}
	
}
