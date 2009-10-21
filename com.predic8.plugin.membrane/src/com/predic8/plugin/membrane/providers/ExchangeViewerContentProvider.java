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

package com.predic8.plugin.membrane.providers;

import org.eclipse.swt.widgets.Display;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.model.IExchangeViewerListener;
import com.predic8.plugin.membrane.viewers.ExchangeViewer;

public class ExchangeViewerContentProvider implements IExchangeViewerContentProvider, IExchangeViewerListener {

	ExchangeViewer exchangeViewer;

	public ExchangeViewerContentProvider(ExchangeViewer viewer) {
		exchangeViewer = viewer;

	}

	public Request getRequest(Exchange exchange) {
		if (exchange == null)
			return null;
		return exchange.getRequest();
	}

	public Response getResponse(Exchange exchange) {
		if (exchange == null)
			return null;
		return exchange.getResponse();
	}

	public void inputChanged(ExchangeViewer viewer, Exchange oldExchange, Exchange newExchange) {
		if (newExchange != null)
			newExchange.addExchangeViewerListener(this);
		if (oldExchange != null)
			oldExchange.removeExchangeViewerListener(this);

	}

	public void addRequest(final Request request) {
		if (request != null) {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					exchangeViewer.setRequest(request);
				}
			});
		}
	}

	public void removeRequest() {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				exchangeViewer.setRequest(null);
			}
		});
	}

	public void addResponse(final Response response) {
		if (response != null) {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					exchangeViewer.setResponse(response);
					exchangeViewer.updateUIStatus();
				}
			});
		}

	}

	public void removeResponse() {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				exchangeViewer.setResponse(null);
			}
		});

	}

	public void removeExchange() {
		removeRequest();
		removeResponse();
	}

	public void setExchangeFinished() {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				exchangeViewer.updateUIStatus();
			}
		});
	}

}
