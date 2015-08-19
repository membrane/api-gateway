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

package com.predic8.membrane.core;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.LimitedMemoryExchangeStore;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;

public class LimitedMemoryExchangeStoreTest {

	private static LimitedMemoryExchangeStore store;

	@Before
	public void setUp() throws Exception {
		store = new LimitedMemoryExchangeStore();
	}

	@Test
	public void testStore() throws Exception {

		store.setMaxSize(5000);

		store.snap(getExchange("0"), Flow.RESPONSE);
		Exchange exc = getExchange("1");
		store.snap(exc, Flow.RESPONSE);

		Assert.assertEquals(2, store.getAllExchangesAsList().size());
		assertStore(0, "0");
		assertStore(1, "1");

		store.setMaxSize(store.getCurrentSize() + 1);

		store.snap(getExchange("2"), Flow.RESPONSE);

		Assert.assertEquals(2, store.getAllExchangesAsList().size());
		assertStore(0, "1");
		assertStore(1, "2");

	}

	private void assertStore(int pos, String value) {
		Assert.assertEquals(value, store.getAllExchangesAsList().get(pos).getProperty("id"));
	}

	private Exchange getExchange(String id) throws IOException {
		Exchange exc = new Exchange(null);
		exc.setProperty("id", id);
		Request req = new Request();
		req.create("GET", "http://test", "HTTP/", new Header(), null);
		exc.setRequest(req);
		exc.setResponse(Response.ok().body("<xml />").build());
		return exc;
	}

}
