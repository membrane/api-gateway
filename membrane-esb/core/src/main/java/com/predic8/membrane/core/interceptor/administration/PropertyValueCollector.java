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
		clients.add(exc.getSourceHostname());
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
