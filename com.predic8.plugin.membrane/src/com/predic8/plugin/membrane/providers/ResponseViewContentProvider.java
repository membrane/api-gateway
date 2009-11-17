package com.predic8.plugin.membrane.providers;

import org.eclipse.swt.widgets.Display;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.plugin.membrane.views.AbstractMessageView;

public class ResponseViewContentProvider extends MessageViewContentProvider {

	public ResponseViewContentProvider(AbstractMessageView messageView) {
		super(messageView);
	}

	@Override
	public Message getMessage(Exchange exchange) {
		if (exchange == null)
			return null;
		return exchange.getResponse();
	}

	public void addRequest(Request request) {
		
	}

	public void addResponse(final Response response) {
		if (response != null) {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					messageView.setMessage(response);
					messageView.updateUIStatus(false);
				}
			});
		}
	}

}
