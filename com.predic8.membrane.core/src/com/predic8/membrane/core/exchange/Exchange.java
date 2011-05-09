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

import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.transport.http.AbstractHttpRunnable;

public class Exchange extends AbstractExchange {
	
	private AbstractHttpRunnable serverThread;
	
	private String originalHostHeader = "";
	
	public Exchange() {
		
	}
	
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
		return new ForwardingRuleKey(request.getHeader().getHost(), request.getMethod(), request.getUri(), serverThread.getSourceSocket().getLocalPort());
	}
	
}
