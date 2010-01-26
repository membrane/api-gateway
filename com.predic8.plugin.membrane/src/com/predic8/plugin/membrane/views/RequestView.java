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

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolItem;

import com.predic8.membrane.core.exchange.ExchangeState;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.contentproviders.RequestViewContentProvider;
import com.predic8.plugin.membrane.resources.ImageKeys;
import com.predic8.plugin.membrane.viewcomponents.RequestComp;

public class RequestView extends AbstractMessageView {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.RequestView";

	private ToolItem itemResendRequest;

	public RequestView() {

	}

	@Override
	public void createPartControl(final Composite parent) {
		super.createPartControl(parent);

		itemResendRequest = new ToolItem(toolBar, SWT.PUSH);
		itemResendRequest.setText("Resend");
		ImageDescriptor descriptorResend = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_ARROW_REDO);

		Image iconResend = descriptorResend.createImage();
		itemResendRequest.setImage(iconResend);
		itemResendRequest.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				((RequestComp) baseComp).resendRequest();
			}
		});
		itemResendRequest.setEnabled(false);

		baseComp = new RequestComp(partComposite, SWT.NONE, this);
		baseComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		contentProvider = new RequestViewContentProvider(this);
	}

	@Override
	public void updateUIStatus(boolean canShowBody) {
		if (exchange == null) {
			itemResendRequest.setEnabled(false);
		} else {
			if (exchange.getStatus() != ExchangeState.STARTED) {
				itemResendRequest.setEnabled(true);
			} else {
				itemResendRequest.setEnabled(false);
			}

			if (exchange.getRule().isBlockRequest()) {
				itemContinue.setEnabled(baseComp.isContinueEnabled());
			} else {
				itemContinue.setEnabled(false);
			}
		}
		baseComp.updateUIStatus(exchange, canShowBody);

	}

	@Override
	public void setFocus() {

	}

	public void setRequestFormatEnabled(boolean status) {
		itemFormat.setEnabled(status);
	}

	public void setRequestSaveEnabled(boolean status) {
		itemSave.setEnabled(status);
	}

	public void setResponseFormatEnabled(boolean status) {
		//ignore
	}

	public void setResponseSaveEnabled(boolean status) {
		//ignore
	}

}
