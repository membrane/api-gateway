/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.proxies.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

public class AbstractInterceptor implements Interceptor {

	protected String name = this.getClass().getName();

	private EnumSet<Flow> flow = REQUEST_RESPONSE_ABORT;

	protected Router router;

	public AbstractInterceptor() {
		super();
	}

	public Outcome handleRequest(Exchange exc) throws Exception {
		return CONTINUE;
	}

	public Outcome handleResponse(Exchange exc) throws Exception {
		return CONTINUE;
	}

	public void handleAbort(Exchange exchange) {
		// do nothing
	}

	public String getDisplayName() {
		return name;
	}

	public void setDisplayName(String name) {
		this.name = name;
	}

	public void setFlow(EnumSet<Flow> flow) {
		this.flow = flow;
	}

	public EnumSet<Flow> getFlow() {
		return flow;
	}

	@Override
	public boolean handlesRequests() {
		return flow.contains(Flow.REQUEST);
	}

	@Override
	public boolean handlesResponses() {
		return flow.contains(Flow.RESPONSE);
	}

	@Override
	public String getShortDescription() {
		return "";
	}

	@Override
	public String getLongDescription() {
		return getShortDescription();
	}

	@Override
	public final String getHelpId() {
		MCElement annotation = getClass().getAnnotation(MCElement.class);
		if (annotation == null)
			return null;
		if (!annotation.id().isEmpty())
			return annotation.id();
		return annotation.name();
	}

	/**
	 * Called after parsing is complete and this has been added to the object tree (whose root is Router).
	 */
	public void init() throws Exception {
		// do nothing here - override in subclasses.
	}

	public void init(Router router) throws Exception {
		this.router = router;
		init();
	}

	public <T extends Proxy> T getProxy(){
		return (T)getRouter()
				.getRuleManager()
				.getRules()
				.stream()
				.filter(proxy -> proxy
						.getInterceptors() != null)
				.filter(proxy -> proxy
						.getInterceptors()
						.stream().anyMatch(this::hasSameReferenceAs))
				.findAny()
				.get();
	}

	private boolean hasSameReferenceAs(Interceptor i){
		if(i instanceof AbstractFlowInterceptor){
			return ((AbstractFlowInterceptor) i).getInterceptors().stream().anyMatch(this::hasSameReferenceAs);
		}
		return i == this;
	}

	public Router getRouter() { //wird von ReadRulesConfigurationTest aufgerufen.
		return router;
	}

	public static Message getMessage(Exchange exc, Interceptor.Flow flow) {
		return switch (flow) {
			case REQUEST -> exc.getRequest();
			case RESPONSE, ABORT ->  exc.getResponse();
		};
	}
}