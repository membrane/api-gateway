/* Copyright 2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.util.URIFactory;

public class IllegalCharactersInURLTest {

	private HttpRouter r;

	@Before
	public void init() throws Exception {
		r = new HttpRouter();
		r.setHotDeploy(false);
		r.add(new ServiceProxy(new ServiceProxyKey(3027), "localhost", 3028));
		ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey(3028), null, 80);
		sp2.getInterceptors().add(new AbstractInterceptor() {
			@Override
			public Outcome handleRequest(Exchange exc) throws Exception {
				Assert.assertEquals("/foo{}", exc.getRequestURI());
				exc.setResponse(Response.ok().build());
				return Outcome.RETURN;
			}
		});
		r.add(sp2);
		r.start();
	}

	@After
	public void uninit() {
		r.stop();
	}

	@Test(expected=IllegalArgumentException.class)
	public void apacheHttpClient() throws Exception {
		CloseableHttpClient hc = HttpClientBuilder.create().build();
		HttpResponse res = hc.execute(new HttpGet("http://localhost:3027/foo{}"));
		Assert.assertEquals(200, res.getStatusLine().getStatusCode());
	}

	@Test
	public void doit() throws Exception {
		URIFactory uriFactory = new URIFactory(true);
		Response res = new HttpClient().call(new Request.Builder().method("GET").url(uriFactory, "http://localhost:3027/foo{}").body("").buildExchange()).getResponse();
		Assert.assertEquals(200, res.getStatusCode());
	}
}
