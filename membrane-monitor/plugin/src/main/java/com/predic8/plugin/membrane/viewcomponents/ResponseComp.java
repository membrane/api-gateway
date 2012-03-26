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

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.ExchangeState;
import com.predic8.membrane.core.rules.Rule;

public class ResponseComp extends BaseComp {

	public ResponseComp(Composite parent, int style, IBaseCompositeHost host) {
		super(parent, style, host);
	}

	public void updateUIStatus(AbstractExchange exchange, boolean canShowBody) {
		if (exchange == null) {
			setMessageEditable(false);
		} else if (exchange.getErrorMessage() != null && !exchange.getErrorMessage().equals("")) {
			handleError(exchange.getErrorMessage());
		} else {
			Rule rule = exchange.getRule();
			if (rule.isBlockResponse() && exchange.getStatus() != ExchangeState.FAILED && exchange.getStatus() != ExchangeState.COMPLETED && exchange.getResponse() != null) {
				setMessageEditable(true);
			} else {
				setMessageEditable(false);
			}
		}
		if (canShowBody)
			tabManager.setSelectionOnBodyTabItem();
	}

	@Override
	public void setFormatEnabled(boolean status) {
		compositeHost.setResponseFormatEnabled(status);
	}

	@Override
	public void setSaveEnabled(boolean status) {
		compositeHost.setResponseSaveEnabled(status);
	}
	
	@Override
	public String getTabCompositeName() {
		return "Response Composite";
	}

}