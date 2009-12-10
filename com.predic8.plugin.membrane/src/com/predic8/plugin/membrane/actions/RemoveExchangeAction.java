package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;

public class RemoveExchangeAction extends Action {

	private TableViewer tableViewer;

	public RemoveExchangeAction(TableViewer tViewer) {
		this.tableViewer = tViewer;
		setText("Remove");
		setId("remove exchange action");
	}

	@Override
	public void run() {
		IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
		Object selectedItem = selection.getFirstElement();
		if (selectedItem instanceof Exchange) {
			Exchange selectedExchange = (Exchange) selectedItem;
			selectedExchange.finishExchange(false);// Don't need to refresh.
			Router.getInstance().getExchangeStore().remove(selectedExchange);
			tableViewer.setSelection(null);
			return;
		}
	}
	
}
