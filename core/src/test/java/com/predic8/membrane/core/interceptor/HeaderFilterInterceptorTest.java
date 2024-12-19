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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.HeaderFilterInterceptor.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.google.common.collect.Lists.*;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.HeaderFilterInterceptor.Action.*;
import static org.junit.jupiter.api.Assertions.*;

public class HeaderFilterInterceptorTest {

	@Test
	public void doit() throws Exception {
		Exchange exc = new Exchange(null);
		exc.setResponse(ok().header("a", "b").header("c", "d").header("c", "d2").header("e", "f").build());

		HeaderFilterInterceptor fhi = new HeaderFilterInterceptor();
		fhi.setRules(newArrayList(new Rule("Server", REMOVE), // implicitly set by Response.ok()
                new Rule("a", KEEP),
                new Rule("c.*", REMOVE)));
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
		exc.setResponse(ok().header("Strict-Transport-Security", "foo").build());

		var filter = new HeaderFilterInterceptor();
		filter.setRules(List.of(new Rule("strict-transport-security", REMOVE)));

		filter.handleResponse(exc);

		assertFalse(exc.getResponse().getHeader().contains("STRICT-TRANSPORT-SECURITY"));
	}

	@Test
	void removeWildcard() throws Exception {
		var exc = new Exchange(null);
		exc.setResponse(ok()
				.header("X-Foo", "foo")
				.header("Xtreme", "true")
				.header("X-Bar","bar").build());

		var filter = new HeaderFilterInterceptor();
		filter.setRules(List.of(new Rule("X-.*", REMOVE)));

		filter.handleResponse(exc);

		assertNotNull(exc.getResponse().getHeader().getFirstValue("Xtreme"));
		assertNull(exc.getResponse().getHeader().getFirstValue("X-Bar"));
		assertNull(exc.getResponse().getHeader().getFirstValue("X-Foo"));
	}
}
