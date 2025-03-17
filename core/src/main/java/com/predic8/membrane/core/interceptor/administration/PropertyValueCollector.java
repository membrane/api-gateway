package com.predic8.membrane.core.interceptor.administration;

import java.util.HashSet;
import java.util.Set;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchangestore.ExchangeCollector;

import static com.predic8.membrane.core.interceptor.administration.AdminRESTInterceptor.getClientAddr;

public class PropertyValueCollector implements ExchangeCollector {

	Set<Integer> statusCodes = new HashSet<>();
	Set<String> proxies = new HashSet<>();
	Set<String> reqContentTypes = new HashSet<>();
	Set<String> respContentTypes = new HashSet<>();
	Set<String> methods = new HashSet<>();
	Set<String> clients = new HashSet<>();
	Set<String> servers = new HashSet<>();

	boolean useXForwardedForAsClientAddr;

	public void setUseXForwardedForAsClientAddr(boolean useXForwardedForAsClientAddr) {
		this.useXForwardedForAsClientAddr = useXForwardedForAsClientAddr;
	}

	@Override
	public void collect(AbstractExchange exc) {
		if (exc.getResponse() != null) {
			statusCodes.add(exc.getResponse().getStatusCode());
		}

		// Check Proxy for null before adding
		if (exc.getProxy() != null) {
			proxies.add(exc.getProxy().toString());
		} else {
			proxies.add("undefined");  // handle exchanges without proxy
		}

		reqContentTypes.add(exc.getRequestContentType());
		respContentTypes.add(exc.getResponseContentType());

		if (exc.getRequest() != null) {
			methods.add(exc.getRequest().getMethod());
		} else {
			methods.add("UNKNOWN");
		}

		clients.add(getClientAddr(useXForwardedForAsClientAddr, exc));

		if (exc.getServer() != null) {
			servers.add(exc.getServer());
		} else {
			servers.add("undefined");
		}
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