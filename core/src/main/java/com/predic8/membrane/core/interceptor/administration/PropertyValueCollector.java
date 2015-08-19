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

public class PropertyValueCollector implements ExchangeCollector{

	Set<Integer> statusCodes = new HashSet<Integer>();
	Set<String> proxies = new HashSet<String>();
	Set<String> reqContentTypes = new HashSet<String>();
	Set<String> respContentTypes = new HashSet<String>();
	Set<String> methods = new HashSet<String>();
	Set<String> clients = new HashSet<String>();
	Set<String> servers = new HashSet<String>();

	public void collect(AbstractExchange exc) {
		if (exc.getResponse() != null) {
			statusCodes.add(exc.getResponse().getStatusCode());
		}

		proxies.add(exc.getRule().toString());
		reqContentTypes.add(exc.getRequestContentType());
		respContentTypes.add(exc.getResponseContentType());
		methods.add(exc.getRequest().getMethod());
		clients.add(exc.getRemoteAddr());
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
