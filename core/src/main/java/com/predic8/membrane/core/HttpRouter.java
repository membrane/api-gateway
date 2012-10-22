/* Copyright 2009, 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core;

import java.util.ArrayList;
import java.util.List;

import com.predic8.membrane.core.interceptor.DispatchingInterceptor;
import com.predic8.membrane.core.interceptor.HTTPClientInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.RuleMatchingInterceptor;
import com.predic8.membrane.core.interceptor.UserFeatureInterceptor;
import com.predic8.membrane.core.io.ConfigurationFileStore;
import com.predic8.membrane.core.transport.Transport;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class HttpRouter extends Router {

	public HttpRouter() {
		configurationManager.setConfigurationStore(new ConfigurationFileStore());
		transport = createTransport();
	}
	
	/**
	 * Same as the default config from monitor-beans.xml
	 */
	public Transport createTransport() {
		Transport transport = new HttpTransport();
		List<Interceptor> interceptors = new ArrayList<Interceptor>();
		interceptors.add(new RuleMatchingInterceptor());		
		interceptors.add(new DispatchingInterceptor());
		interceptors.add(new UserFeatureInterceptor());
		interceptors.add(new HTTPClientInterceptor());
		transport.setInterceptors(interceptors);
		try {
			transport.init(this);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return transport;
	}
	
	@Override
	public HttpTransport getTransport() {
		return (HttpTransport)transport;
	}
	
	public void addUserFeatureInterceptor(Interceptor i) {
		List<Interceptor> is = getTransport().getInterceptors();
		is.add(is.size()-2, i);
	}
	
}
