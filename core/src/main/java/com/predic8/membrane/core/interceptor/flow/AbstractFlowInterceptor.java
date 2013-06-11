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
