/* Copyright 2009, 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.rewrite.*;
import com.predic8.membrane.core.model.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.ssl.*;
import com.predic8.membrane.core.util.*;

import java.io.*;
import java.util.*;

public abstract class Transport {

	/**
	 * SSL and Non-SSL are mixed here, maybe split that in future
	 */
	protected Set<IPortChangeListener> menuListeners = new HashSet<>();

	private List<Interceptor> interceptors = new Vector<>();
	private Router router;
	private boolean reverseDNS = true;

	private int concurrentConnectionLimitPerIp = -1;

	public String getOpenBackendConnections(int port){
		return "N/A";
	}

	public List<Interceptor> getInterceptors() {
		return interceptors;
	}

	@MCChildElement(allowForeign=true)
	public void setInterceptors(List<Interceptor> interceptors) {
		this.interceptors = interceptors;
	}

	public void init(Router router) throws Exception {
		this.router = router;

		if (interceptors.isEmpty()) {
			interceptors.add(new RuleMatchingInterceptor());
			interceptors.add(new LoggingContextInterceptor());
			interceptors.add(new ExchangeStoreInterceptor(router.getExchangeStore()));
			interceptors.add(new DispatchingInterceptor());
			interceptors.add(new ReverseProxyingInterceptor());
			interceptors.add(new UserFeatureInterceptor());
			interceptors.add(new InternalRoutingInterceptor());
			interceptors.add(new HTTPClientInterceptor());
		}

		for (Interceptor interceptor : interceptors) {
			interceptor.init(router);
		}
	}

	public Router getRouter() {
		return router;
	}

	public <T extends Interceptor> Optional<T> getFirstInterceptorOfType(Class<T> type) {
		return InterceptorUtil.getFirstInterceptorOfType(interceptors, type);
	}

	public void closeAll() {
		closeAll(true);
	}

	public void closeAll(boolean waitForCompletion) {}

	public void openPort(String ip, int port, SSLProvider sslProvider, TimerManager timerManager) throws IOException {}

	public void openPort(SSLableProxy proxy, TimerManager timerManager) throws IOException {}

	public abstract boolean isOpeningPorts();

	public boolean isReverseDNS() {
		return reverseDNS;
	}

	/**
	 * @description Whether the remote address should automatically reverse-looked up for incoming connections.
	 * @default true
	 */
	@MCAttribute
	public void setReverseDNS(boolean reverseDNS) {
		this.reverseDNS = reverseDNS;
	}

	public int getConcurrentConnectionLimitPerIp() {
		return concurrentConnectionLimitPerIp;
	}

	/**
	 * @description Limits the number of concurrent connections from one ip
	 * @default -1 No Limit
	 */
	@MCAttribute
	public void setConcurrentConnectionLimitPerIp(int concurrentConnectionLimitPerIp) {
		this.concurrentConnectionLimitPerIp = concurrentConnectionLimitPerIp;
	}
}
