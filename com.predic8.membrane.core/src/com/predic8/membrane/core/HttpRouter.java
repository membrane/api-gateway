package com.predic8.membrane.core;

import java.util.ArrayList;
import java.util.List;

import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.RoutingInterceptor;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class HttpRouter extends Router {

	public HttpRouter() {
		ruleManager = new RuleManager();
		exchangeStore = new ForgetfulExchangeStore();
		transport = new HttpTransport();
		configurationManager = new ConfigurationManager();
		List<Interceptor> interceptors = new ArrayList<Interceptor>();
		RoutingInterceptor routingeInterceptor = new RoutingInterceptor();
		routingeInterceptor.setRuleManager(ruleManager);
		interceptors.add(routingeInterceptor);
		
		transport.setInterceptors(interceptors);
	}
	
	@Override
	public HttpTransport getTransport() {
		return (HttpTransport)transport;
	}
	
}
