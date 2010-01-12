package com.predic8.plugin.membrane.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;

import com.predic8.membrane.core.exchange.ExchangeState;
import com.predic8.plugin.membrane.contentproviders.ResponseViewContentProvider;
import com.predic8.plugin.membrane.viewcomponents.ResponseComp;

public class ResponseView extends AbstractMessageView {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.ResponseView";

	public ResponseView() {

	}

	@Override
	public void createPartControl(final Composite parent) {
		super.createPartControl(parent);

		baseComp = new ResponseComp(partComposite, SWT.NONE, this);
		baseComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		contentProvider = new ResponseViewContentProvider(this);
	}

	@Override
	public void updateUIStatus(boolean canShowBody) {
		if (exchange != null) {
			if (exchange.getRule().isBlockResponse()
					&& exchange.getStatus() != ExchangeState.COMPLETED
					&& exchange.getStatus() != ExchangeState.FAILED
					&& exchange.getResponse() != null) {
				itemContinue.setEnabled(baseComp.isContinueEnabled());
			} else {
				itemContinue.setEnabled(false);
			}
		}
		baseComp.updateUIStatus(exchange, canShowBody);
	}

	public void setRequestFormatEnabled(boolean status) {
		//ignore
	}

	public void setRequestSaveEnabled(boolean status) {
		//ignore
		
	}

	public void setResponseFormatEnabled(boolean status) {
		itemFormat.setEnabled(status); 
	}

	public void setResponseSaveEnabled(boolean status) {
		itemSave.setEnabled(status);
	}

}
