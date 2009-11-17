package com.predic8.plugin.membrane.providers;

import org.eclipse.swt.widgets.Display;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.plugin.membrane.views.AbstractMessageView;

public class RequestViewContentProvider extends MessageViewContentProvider {

	public RequestViewContentProvider(AbstractMessageView messageView) {
		super(messageView);
	}

	@Override
	public Message getMessage(Exchange exchange) {
		if (exchange == null)
			return null;
		return exchange.getRequest();
	}

	public void addRequest(final Request request) {
		if (request != null) {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					messageView.setMessage(request);
					messageView.updateUIStatus(false);
				}
			});
		}
	}

	public void addResponse(Response response) {
		
	}
	
}
