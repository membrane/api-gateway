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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.transport.http.HttpResendThread;


public class RequestComp extends BaseComp {

	public RequestComp(Composite parent, int style, IBaseCompositeHost host) {
		super(parent, style, host);
	}

	public void updateUIStatus(Exchange exchange, boolean canShowBody) {
		if (exchange == null) {
			setMessageEditable(false);
		} else {
			setMessageEditable(true);
		}
		if (canShowBody)
			tabManager.setSelectionOnBodyTabItem();
	}

	public void resendRequest() {
		if (msg != null) {
			if (isBodyModified()) {
				tabManager.setBodyModified(false);
				copyBodyFromGUIToModel();
			}
			(new HttpResendThread((HttpExchange)getCompositeHost().getExchange())).start();
		}
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
