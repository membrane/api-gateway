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
	
	public void setExchangeStopped() {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				messageView.updateUIStatus(false);
			}
		});
	}
}
