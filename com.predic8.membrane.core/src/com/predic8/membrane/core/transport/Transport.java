/* Copyright 2009 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.model.IPortChangeListener;

public class Transport {

	protected Set<IPortChangeListener> menuListeners = new HashSet<IPortChangeListener>();
	
	private List<Interceptor> interceptors = new Vector<Interceptor>();

	private Router router;
	
	private void setRouterForInterceptors() {	
		for (Interceptor interceptor : interceptors) {
			interceptor.setRouter(router);
		}
	}

	public List<Interceptor> getInterceptors() {
		return interceptors;
	}

	public void setInterceptors(List<Interceptor> interceptors) {
		this.interceptors = interceptors;
	}

	public void setRouter(Router router) {
		this.router = router;
		setRouterForInterceptors();
	}
	
	public Router getRouter() {
		return router;
	}
	
	public synchronized void closeAll() throws IOException {
		
	}
}
