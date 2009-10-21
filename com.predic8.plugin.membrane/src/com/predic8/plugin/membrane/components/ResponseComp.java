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

package com.predic8.plugin.membrane.components;

import org.eclipse.swt.widgets.Composite;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.ExchangeState;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.viewers.ExchangeViewer;

public class ResponseComp extends BaseComp {

	public ResponseComp(Composite parent, int style, ExchangeViewer exchangeViewer) {
		super(parent, style, exchangeViewer);
	}

	public void updateUIStatus(Exchange exchange) {
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
	}

	@Override
	public void setFormatEnabled(boolean status) {
		exchangeViewer.setResponseFormatEnabled(status);
	}

	@Override
	public void setSaveEnabled(boolean status) {
		exchangeViewer.setResponseSaveEnabled(status);
	}
	
	@Override
	public String getTabCompositeName() {
		return "Response Composite";
	}

}