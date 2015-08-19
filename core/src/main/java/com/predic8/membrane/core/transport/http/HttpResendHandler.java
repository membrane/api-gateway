/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

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
import java.net.InetAddress;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.util.EndOfStreamException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class HttpResendHandler extends AbstractHttpHandler implements Runnable {
	private static Log log = LogFactory.getLog(HttpResendHandler.class.getName());

	public HttpResendHandler(Exchange exc, HttpTransport transport) {
		super(transport);
		exchange = new Exchange(exc, this);

		srcReq = exc.getRequest();
	}

	public void run() {
		try {
			exchange.setRequest(srcReq);
			try {
				invokeHandlers();
			} catch (AbortException e) {
				exchange.finishExchange(true, exchange.getErrorMessage());
				return;
			}
			exchange.setCompleted();
			return;
		} catch (IOException e) {
			log.warn("", e);
		} catch (EndOfStreamException e) {
			log.warn("", e);
		}
	}

	@Override
	public void shutdownInput() {
		// do nothing
	}

	@Override
	public InetAddress getLocalAddress() {
		return null;
	}

	@Override
	public int getLocalPort() {
		return 0;
	}

}