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

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.util.EndOfStreamException;


public class HttpResendThread extends AbstractHttpThread {

	private Rule rule;

	public HttpResendThread(HttpExchange exc, HttpTransport transport) {
		this.transport = transport;
		exchange = new HttpExchange(exc);
		exchange.setServerThread(this);
		
		srcReq = exc.getRequest();
		this.rule = exc.getRule();
	}

	public void run() {
		try {
			exchange.setRequest(srcReq);

			exchange.setRule(rule);
			
			//TODO call interceptors
			Router.getInstance().getExchangeStore().add(exchange);
		
			if (rule instanceof ForwardingRule) {
				if (((ForwardingRule) rule).getTargetHost() != null && ((ForwardingRule) rule).getTargetHost().length() != 0) {
					targetRes = client.call(exchange);
					targetRes.readBody();
					client.close();
				}
			} else if (rule instanceof ProxyRule) {
				targetRes = client.call(exchange);
				targetRes.readBody();
				client.close();
			}
			
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