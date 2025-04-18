/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.administration;

import java.util.HashSet;
import java.util.Set;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchangestore.ExchangeCollector;

import static com.predic8.membrane.core.interceptor.administration.AdminRESTInterceptor.getClientAddr;

public class PropertyValueCollector implements ExchangeCollector{

	final Set<Integer> statusCodes = new HashSet<>();
	final Set<String> proxies = new HashSet<>();
	final Set<String> reqContentTypes = new HashSet<>();
	final Set<String> respContentTypes = new HashSet<>();
	final Set<String> methods = new HashSet<>();
	final Set<String> clients = new HashSet<>();
	final Set<String> servers = new HashSet<>();

    boolean useXForwardedForAsClientAddr;

    public void setUseXForwardedForAsClientAddr(boolean useXForwardedForAsClientAddr) {
        this.useXForwardedForAsClientAddr = useXForwardedForAsClientAddr;
    }

    public void collect(AbstractExchange exc) {
		if (exc.getResponse() != null) {
			statusCodes.add(exc.getResponse().getStatusCode());
		}
        proxies.add(exc.getProxy() != null ? exc.getProxy().toString() : "No Proxy");
		reqContentTypes.add(exc.getRequestContentType());
		respContentTypes.add(exc.getResponseContentType());
		methods.add(exc.getRequest().getMethod());
		clients.add(getClientAddr(useXForwardedForAsClientAddr, exc));
		servers.add(exc.getServer());
	}

	public Set<Integer> getStatusCodes() {
		return statusCodes;
	}

	public Set<String> getProxies() {
		return proxies;
	}

	public Set<String> getReqContentTypes() {
		return reqContentTypes;
	}

	public Set<String> getRespContentTypes() {
		return respContentTypes;
	}

	public Set<String> getMethods() {
		return methods;
	}

	public Set<String> getClients() {
		return clients;
	}

	public Set<String> getServers() {
		return servers;
	}
}