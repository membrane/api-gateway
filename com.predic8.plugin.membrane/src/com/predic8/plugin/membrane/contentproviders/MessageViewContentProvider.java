package com.predic8.plugin.membrane.contentproviders;

import org.eclipse.swt.widgets.Display;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.model.IExchangeViewerListener;
import com.predic8.plugin.membrane.views.AbstractMessageView;

public abstract class MessageViewContentProvider implements IExchangeViewerListener {

	protected AbstractMessageView messageView;
	
	
	public MessageViewContentProvider(AbstractMessageView messageView) {
		this.messageView = messageView;
	}
	
	
	public void inputChanged(Exchange oldInput, Exchange newInput) {
		if (newInput != null)
			newInput.addExchangeViewerListener(this);
		if (oldInput != null)
			oldInput.removeExchangeViewerListener(this);
	}

	public abstract Message getMessage(Exchange exchange);

	public void setExchangeFinished() {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				messageView.updateUIStatus(false);
			}
		});
	}
	
	public void removeExchange() {
		messageView.setMessage(null);
	}
}
