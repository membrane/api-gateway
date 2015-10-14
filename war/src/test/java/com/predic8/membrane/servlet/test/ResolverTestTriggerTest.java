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
package com.predic8.membrane.servlet.test;

import static com.predic8.membrane.test.AssertUtils.getAndAssert200;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.http.ParseException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.resolver.SchemaResolver;

public class ResolverTestTriggerTest extends AbstractInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(ResolverTestTriggerTest.class);
	private static final String MAGIC = "MAGIC463634623\n";

	@Override
	public Outcome handleRequest(Exchange exc) {
		try {
			Class<?> clazz = Class.forName("com.predic8.membrane.core.resolver.ResolverTest");
			clazz.getField("deployment").set(null, "J2EE");

			Object value = router.getResolverMap().getFileSchemaResolver();
			Object resolverMap = clazz.getField("resolverMap").get(null);
			resolverMap.getClass().getMethod("addSchemaResolver", SchemaResolver.class).invoke(resolverMap, value);


			Parameterized p = new Parameterized(clazz);
			JUnitCore c = new JUnitCore();
			Result run = c.run(Request.runner(p));

			StringBuilder sb = new StringBuilder();

			sb.append(MAGIC);

			for (Failure f : run.getFailures()) {
				sb.append(f.toString());
				StringWriter stringWriter = new StringWriter();
				f.getException().printStackTrace(new PrintWriter(stringWriter));
				sb.append(stringWriter.toString());
				sb.append("\n");
				sb.append("\n");
			}

			exc.setResponse(Response.ok().header(Header.CONTENT_TYPE, MimeType.TEXT_PLAIN_UTF8).body(sb.toString()).build());

		} catch (Throwable t) {
			LOG.error(t.getMessage(), t);
		}
		return Outcome.RETURN;
	}

	@Test
	public void run() throws ParseException, IOException {
		Assert.assertEquals(MAGIC, getAndAssert200("http://localhost:3021/test/"));
	}

}
