package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.resources.ImageKeys;

public class ExchangeStopAction extends Action {

	private TreeViewer treeView;
	private ImageDescriptor descriptor_enable;
	private ImageDescriptor descriptor_disable;
	
	public ExchangeStopAction(TreeViewer treeView) {
		this.treeView = treeView;
		setText("Stop");
		setId("Exchange Stop Action");
		descriptor_enable = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_STOP_ENABLED);
		descriptor_disable = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_STOP_DISABLED);
	}
	
	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		if(enabled)
			setImageDescriptor(descriptor_enable);
		else
			setImageDescriptor(descriptor_disable);
	}
	public void run() {
		IStructuredSelection selection = (IStructuredSelection) treeView.getSelection();
		Object selectedItem = selection.getFirstElement();

		if (selectedItem instanceof Exchange) {
			((Exchange)selectedItem).finishExchange(true);
			treeView.setSelection(selection);
		}
	}
	
	
}
