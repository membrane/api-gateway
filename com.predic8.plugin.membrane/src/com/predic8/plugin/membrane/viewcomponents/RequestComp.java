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

package com.predic8.plugin.membrane.viewcomponents;

import org.eclipse.swt.widgets.Composite;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.transport.http.HttpResendRunnable;
import com.predic8.membrane.core.transport.http.HttpTransport;


public class RequestComp extends BaseComp {

	public RequestComp(Composite parent, int style, IBaseCompositeHost host) {
		super(parent, style, host);
	}

	public void updateUIStatus(AbstractExchange exchange, boolean canShowBody) {
		setMessageEditable(exchange != null);
		if (canShowBody)
			tabManager.setSelectionOnBodyTabItem();
	}

	public void resendRequest() {
		if (msg != null) {
			if (isBodyModified()) {
				tabManager.setBodyModified(false);
				copyBodyFromGUIToModel();
			}
			new Thread(new HttpResendRunnable(getCompositeHost().getExchange(), getTransport())).start();
		}
	}

	private HttpTransport getTransport() {
		return (HttpTransport)Router.getInstance().getTransport();
	}
	
	@Override
	public void setFormatEnabled(boolean status) {
		compositeHost.setRequestFormatEnabled(status);
	}

	@Override
	public String getTabCompositeName() {
		return "Request Composite";
	}

	@Override
	public void setSaveEnabled(boolean status) {
		compositeHost.setRequestSaveEnabled(status);
	}
}
