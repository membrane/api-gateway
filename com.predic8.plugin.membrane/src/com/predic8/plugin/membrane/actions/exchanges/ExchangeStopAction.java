/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.plugin.membrane.actions.exchanges;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.resources.ImageKeys;

public class ExchangeStopAction extends Action {

	private StructuredViewer treeView;
	private ImageDescriptor descriptor_enable;
	private ImageDescriptor descriptor_disable;
	
	public ExchangeStopAction(StructuredViewer treeView) {
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
