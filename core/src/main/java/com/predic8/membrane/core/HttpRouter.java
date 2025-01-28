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

import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.transport.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;

import java.util.*;

public class HttpRouter extends Router {

	public HttpRouter() {
		this(null);
	}

	public HttpRouter(ProxyConfiguration proxyConfiguration) {
		transport = createTransport();
		resolverMap.getHTTPSchemaResolver().getHttpClientConfig().setProxy(proxyConfiguration);
	}

	/**
	 * Same as the default config from monitor-beans.xml
	 */
	private Transport createTransport() {
		Transport transport = new HttpTransport();
		List<Interceptor> interceptors = new ArrayList<>();
		interceptors.add(new RuleMatchingInterceptor());
		interceptors.add(new DispatchingInterceptor());
		interceptors.add(new UserFeatureInterceptor());
		interceptors.add(new InternalRoutingInterceptor());
		HTTPClientInterceptor httpClientInterceptor = new HTTPClientInterceptor();
		interceptors.add(httpClientInterceptor);
		transport.setInterceptors(interceptors);
		return transport;
	}

	@Override
	public HttpTransport getTransport() {
		return (HttpTransport)transport;
	}

	/**
	 * TODO Only used for tests. It s brittle cause it is dependent on
	 * the list. In tests interceptors in the proxy can be used instead.
	 */
	public void addUserFeatureInterceptor(Interceptor i) {
		List<Interceptor> is = getTransport().getInterceptors();
		is.add(is.size()-3, i);
	}

}
