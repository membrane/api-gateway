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
package com.predic8.plugin.membrane.views;

import java.io.IOException;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.part.ViewPart;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.contentproviders.MessageViewContentProvider;
import com.predic8.plugin.membrane.resources.ImageKeys;
import com.predic8.plugin.membrane.viewcomponents.BaseComp;
import com.predic8.plugin.membrane.viewcomponents.IBaseCompositeHost;
import com.predic8.plugin.membrane.views.util.MessageSaver;

public abstract class AbstractMessageView extends ViewPart implements IBaseCompositeHost {

	protected Exchange exchange;

	private MessageSaver messageSaver = new MessageSaver();

	protected ToolBar toolBar;

	protected ToolItem itemContinue, itemFormat, itemSave;

	protected BaseComp baseComp;

	protected Composite partComposite;
	
	protected MessageViewContentProvider contentProvider;

	@Override
	public void createPartControl(Composite parent) {
		partComposite = new Composite(parent, SWT.NONE);
		partComposite.setLayout(new GridLayout());

		toolBar = new ToolBar(partComposite, SWT.NONE);

		itemContinue = createItemContinue();

		itemFormat = createItemFormat();

		itemSave = createItemSave();

	}

	private ToolItem createItemContinue() {
		ToolItem item = createToolItem("Continue", ImageKeys.IMAGE_FLAG_GREEN);
		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				baseComp.continuePressed();
			}
		});
		return item;
	}

	private ToolItem createItemFormat() {
		ToolItem item = createToolItem("Format", ImageKeys.IMAGE_FORMAT);
		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					baseComp.beautifyBody();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		});
		return item;
	}

	private ToolItem createItemSave() {
		ToolItem item = createToolItem("Save", ImageKeys.IMAGE_SAVE_MESSAGE);
		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					messageSaver.saveMessage(baseComp.getMsg());
				} catch (Exception e1) {
					MessageDialog.openError(baseComp.getShell(), "Save Error", e1.getMessage());
				}
			}
		});
		return item;
	}
	
	private ToolItem createToolItem(String itemText, String imageKey) {
		ToolItem item = new ToolItem(toolBar, SWT.PUSH);
		item.setText(itemText);
		item.setImage(createImage(imageKey));
		item.setEnabled(false);
		return item;
	}

	private Image createImage(String key) {
		return MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(key).createImage();
	}
	
	public void setInput(Exchange newExchange) {
		if (contentProvider == null)
			return;

		contentProvider.inputChanged(exchange, newExchange);
		this.exchange = newExchange;
		baseComp.setMsg(contentProvider.getMessage(exchange));
	}

	public Exchange getExchange() {
		return exchange;
	}

	@Override
	public void setFocus() {

	}

	public void setMessage(Message message) {
		baseComp.setMsg(message);
	}

	public abstract void updateUIStatus(boolean canShowBody);

}
