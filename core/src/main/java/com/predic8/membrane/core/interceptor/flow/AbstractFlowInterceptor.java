package com.predic8.membrane.core.interceptor.flow;

import java.util.ArrayList;
import java.util.List;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;

public abstract class AbstractFlowInterceptor extends AbstractInterceptor {
	private List<Interceptor> interceptors = new ArrayList<Interceptor>();
	
	public List<Interceptor> getInterceptors() {
		return interceptors;
	}
	
	@MCChildElement(allowForeign=true)
	public void setInterceptors(List<Interceptor> interceptors) {
		this.interceptors = interceptors;
	}
	
	@Override
	public void init(Router router) throws Exception {
		super.init(router);
		for (Interceptor i : interceptors)
			i.init(router);
	}
}
