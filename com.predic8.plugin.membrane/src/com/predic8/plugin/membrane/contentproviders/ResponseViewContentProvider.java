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

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.plugin.membrane.views.AbstractMessageView;

public class ResponseViewContentProvider extends MessageViewContentProvider {

	public ResponseViewContentProvider(AbstractMessageView messageView) {
		super(messageView);
	}

	@Override
	public Message getMessage(AbstractExchange exchange) {
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
