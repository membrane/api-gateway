/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.HeaderFilterInterceptor.Action;
import com.predic8.membrane.core.interceptor.HeaderFilterInterceptor.Rule;

import java.util.List;

public class HeaderFilterInterceptorTest {

	@Test
	public void doit() throws Exception {
		Exchange exc = new Exchange(null);
		exc.setResponse(Response.ok().header("a", "b").header("c", "d").header("c", "d2").header("e", "f").build());

		HeaderFilterInterceptor fhi = new HeaderFilterInterceptor();
		fhi.setRules(Lists.newArrayList(new Rule("Server", Action.REMOVE), // implicitly set by Response.ok()
                new Rule("a", Action.KEEP),
                new Rule("c.*", Action.REMOVE)));
		fhi.handleResponse(exc);

		HeaderField[] h = exc.getResponse().getHeader().getAllHeaderFields();
		assertEquals(2, h.length);
		assertEquals("a", h[0].getHeaderName().toString());
		assertEquals("e", h[1].getHeaderName().toString());
	}

	@Test
	@DisplayName("Remove header from Response")
	void remove() throws Exception {
		var exc = new Exchange(null);
		exc.setResponse(Response.ok().header("Strict-Transport-Security", "foo").build());

		var filter = new HeaderFilterInterceptor();
		filter.setRules(List.of(new Rule("strict-transport-security", Action.REMOVE)));

		filter.handleResponse(exc);

		assertFalse(exc.getResponse().getHeader().contains("STRICT-TRANSPORT-SECURITY"));
	}
}
