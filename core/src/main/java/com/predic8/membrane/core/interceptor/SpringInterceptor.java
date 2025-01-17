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

package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import org.springframework.beans.*;
import org.springframework.context.*;

@MCElement(name="interceptor")
public class SpringInterceptor extends AbstractInterceptor implements ApplicationContextAware {

	private String refid;
	private Interceptor i;
	private ApplicationContext ac;

	/**
	 * @description Spring bean id of the referenced interceptor.
	 * @example myInterceptor
	 */
	@MCAttribute(attributeName="refid")
	public void setRefId(String refid) {
		this.refid = refid;
	}

	public String getRefId() {
		return refid;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		ac = applicationContext;
		i = ac.getBean(refid, Interceptor.class);
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
		return i.handleRequest(exc);
	}

	@Override
	public Outcome handleResponse(Exchange exc) {
		return i.handleResponse(exc);
	}

	@Override
	public void handleAbort(Exchange exchange) {
		i.handleAbort(exchange);
	}

	@Override
	public String getDisplayName() {
		return i.getDisplayName();
	}

	@Override
	public void setDisplayName(String name) {
		i.setDisplayName(name);
	}

	@Override
	public String getShortDescription() {
		return i.getShortDescription();
	}

	@Override
	public String getLongDescription() {
		return i.getLongDescription();
	}

	@Override
	public void init() {
		super.init();
		if (refid != null)
			i = (Interceptor) ac.getBean(refid);
		i.init(router);
	}

	public Interceptor getInner() {
		return i;
	}

	@MCChildElement
	public void setInner(Interceptor i) {
		this.i = i;
	}

}
