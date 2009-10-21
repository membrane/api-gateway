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

package com.predic8.membrane.core.transport.http;

import java.io.IOException;

import com.predic8.membrane.core.Core;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.util.EndOfStreamException;


public class HttpResendThread extends AbstractHttpThread {

	private Rule rule;

	public HttpResendThread(Request request, Rule rule) {
		exchange = new HttpExchange();
		exchange.setServerThread(this);
		srcReq = request;
		this.rule = rule;
	}

	public void run() {
		try {
			exchange.setRequest(srcReq);

			exchange.setRule(rule);
			Core.getExchangeStore().add(exchange);
			
			
			if (rule instanceof ForwardingRule) {
				if (((ForwardingRule) rule).getTargetHost() != null && ((ForwardingRule) rule).getTargetHost().length() != 0) {
					targetRes = client.call(exchange);
					targetRes.readBody();
					client.close();
				}
			} //TODO implement resend for proxy rule 
			
			exchange.setResponse(targetRes);

			exchange.setCompleted();

			return;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (EndOfStreamException e) {
			e.printStackTrace();
		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

}