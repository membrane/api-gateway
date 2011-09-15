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

package com.predic8.membrane.core.exchange;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.TerminateException;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.transport.http.*;

public class Exchange extends AbstractExchange {

	private static Log log = LogFactory.getLog(Exchange.class.getName());

	private AbstractHttpRunnable serverThread;

	private String originalHostHeader = "";

	private Connection targetConnection;

	public Exchange() {

	}

	/**
	 * For HttpResendRunnable
	 * 
	 * @param original
	 */
	public Exchange(Exchange original) {
		super(original);
		originalHostHeader = original.originalHostHeader;
	}

	@Override
	public void close() {

	}

	public AbstractHttpRunnable getServerThread() {
		return serverThread;
	}

	public void setServerThread(AbstractHttpRunnable serverThread) {
		this.serverThread = serverThread;
	}

	public String getOriginalHostHeaderHost() {
		return originalHostHeader.replaceFirst(":.*", "");
	}

	public void blockRequestIfNeeded() throws TerminateException {
		synchronized (getRequest()) {
			if (getRule().isBlockRequest()) {
				setStopped();
				block(getRequest());
			}
		}
	}

	public void blockResponseIfNeeded() throws TerminateException {
		synchronized (getResponse()) {
			if (getRule().isBlockResponse()) {
				setStopped();
				block(getResponse());
			}
		}
	}

	public void block(Message msg) throws TerminateException {
		try {
			log.debug("Message thread waits");
			msg.wait();
			log.debug("Message thread received notify");
			if (isForcedToStop())
				throw new TerminateException("Force the exchange to stop.");
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}

	public String getOriginalHostHeaderPort() {
		return originalHostHeader.replaceFirst(".*:", "");
	}

	public void setOriginalHostHeader(String hostHeader) {
		originalHostHeader = hostHeader;
	}

	@Override
	public void setRequest(Request req) {
		super.setRequest(req);
		setOriginalHostHeader(req.getHeader().getHost());
	}

	public ForwardingRuleKey getForwardingRuleKey() {
		return new ForwardingRuleKey(request.getHeader().getHost(),
				request.getMethod(), request.getUri(), serverThread
						.getSourceSocket().getLocalPort());
	}

	public Connection getTargetConnection() {
		return targetConnection;
	}

	public void setTargetConnection(Connection con) {
		targetConnection = con;
	}
}
