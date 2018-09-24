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

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.DispatchingInterceptor;
import com.predic8.membrane.core.interceptor.ExchangeStoreInterceptor;
import com.predic8.membrane.core.interceptor.HTTPClientInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.RuleMatchingInterceptor;
import com.predic8.membrane.core.interceptor.UserFeatureInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.ReverseProxyingInterceptor;
import com.predic8.membrane.core.model.IPortChangeListener;
import com.predic8.membrane.core.transport.ssl.SSLProvider;

public abstract class Transport {

	protected Set<IPortChangeListener> menuListeners = new HashSet<IPortChangeListener>();

	private List<Interceptor> interceptors = new Vector<Interceptor>();
	private Router router;
	private boolean printStackTrace = false;
	private boolean reverseDNS = true;
	int concurrentConnectionLimitPerIp = 60;

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
			interceptors.add(new ExchangeStoreInterceptor(router.getExchangeStore()));
			interceptors.add(new DispatchingInterceptor());
			interceptors.add(new ReverseProxyingInterceptor());
			interceptors.add(new UserFeatureInterceptor());
			interceptors.add(new HTTPClientInterceptor());
		}

		for (Interceptor interceptor : interceptors) {
			interceptor.init(router);
		}
	}

	public Router getRouter() {
		return router;
	}

	public boolean isPrintStackTrace() {
		return printStackTrace;
	}

	/**
	 * @description Whether the stack traces of exceptions thrown by interceptors should be returned in the HTTP response.
	 * @default false
	 */
	@MCAttribute
	public void setPrintStackTrace(boolean printStackTrace) {
		this.printStackTrace = printStackTrace;
	}

	public void closeAll() throws IOException {
		closeAll(true);
	}

	public void closeAll(boolean waitForCompletion) throws IOException {}
	public void openPort(String ip, int port, SSLProvider sslProvider) throws IOException {}

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
	 * @description limits the number of concurrent connections from one ip
	 * @default 60
	 */
	@MCAttribute
	public void setConcurrentConnectionLimitPerIp(int concurrentConnectionLimitPerIp) {
		this.concurrentConnectionLimitPerIp = concurrentConnectionLimitPerIp;
	}
}
