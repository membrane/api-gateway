/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.rewrite;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.DispatchingInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.Mapping;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.util.MessageUtil;
public class RewriteInterceptorTest {

	private RewriteInterceptor rewriter;
	private Exchange exc;
	private DispatchingInterceptor di;
	private ServiceProxy sp;

	@BeforeEach
	public void setUp() throws Exception {
		HttpRouter router = new HttpRouter();

		di = new DispatchingInterceptor();
		di.init(router);

		sp = new ServiceProxy(new ServiceProxyKey(80, null), "www.predic8.de", 80);
		sp.init(router);

		exc = new Exchange(null);
		exc.setRequest(MessageUtil.getGetRequest("/buy/banana/3"));

		rewriter = new RewriteInterceptor();
		List<Mapping> mappings = new ArrayList<>();
		mappings.add( new Mapping("/buy/(.*)/(.*)", "/buy?item=$1&amount=$2", null));
		rewriter.setMappings(mappings);
		rewriter.init(router);
	}

	@Test
	public void testRewriteWithoutTarget() throws Exception {
		assertEquals(Outcome.CONTINUE, di.handleRequest(exc));
		assertEquals(Outcome.CONTINUE, rewriter.handleRequest(exc));
		assertEquals("/buy?item=banana&amount=3", exc.getDestinations().get(0));
	}

	@Test
	public void testRewrite() throws Exception {
		exc.setRule(sp);

		assertEquals(Outcome.CONTINUE, di.handleRequest(exc));
		assertEquals(Outcome.CONTINUE, rewriter.handleRequest(exc));
		assertEquals("http://www.predic8.de:80/buy?item=banana&amount=3", exc.getDestinations().get(0));
	}

}
