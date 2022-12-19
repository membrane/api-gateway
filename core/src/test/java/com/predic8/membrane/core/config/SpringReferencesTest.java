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
package com.predic8.membrane.core.config;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.LogInterceptor;
import com.predic8.membrane.core.interceptor.SpringInterceptor;
import com.predic8.membrane.core.rules.ServiceProxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SpringReferencesTest {

	private static Router r;

	@BeforeAll
	public static void before() throws MalformedURLException {
		r = Router.init("classpath:/proxies-using-spring-refs.xml");
	}

	@Test
	public void doit() {
		ServiceProxy p = (ServiceProxy) r.getRules().iterator().next();
		List<Interceptor> is = p.getInterceptors();

		assertEquals(LogInterceptor.class, is.get(0).getClass());
		assertEquals(LogInterceptor.class, is.get(1).getClass());
		assertEquals(LogInterceptor.class, is.get(2).getClass());
		assertEquals(SpringInterceptor.class, is.get(3).getClass());
		assertEquals(LogInterceptor.class, is.get(4).getClass());

		SpringInterceptor si = (SpringInterceptor) is.get(3);

		assertSame(is.get(1), is.get(2));
		assertSame(is.get(1), si.getInner());
	}

	@AfterAll
	public static void after() throws IOException {
		r.shutdown();
	}
}
