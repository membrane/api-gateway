/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.groovy;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;
import org.springframework.context.*;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class GroovyInterceptorTest {

	private final static ObjectMapper om = new ObjectMapper();

	private final ApplicationContext applicationContext = mock(ApplicationContext.class);

	HttpRouter router;
	Exchange exc;

	@BeforeEach
	void setup() {
		router = new HttpRouter();
		router.setApplicationContext(applicationContext);
		when(applicationContext.getBean("abc")).thenReturn("OK");
		exc = new Exchange(null);
		exc.setRequest(new Request());
	}

	@Test
	public void testRequest() throws Exception {

		GroovyInterceptor i = new GroovyInterceptor();
		i.setSrc("""
				exc.setProperty('foo', 'bar')
				def b = spring.getBean('abc')
				CONTINUE""");
		i.init(router);

		assertEquals(CONTINUE, i.handleRequest(exc));
		assertEquals("bar", exc.getProperty("foo"));
		verify(applicationContext, times(1)).getBean("abc");
	}

	@Test
	public void testHeader() throws Exception {

		GroovyInterceptor i = new GroovyInterceptor();
		i.setSrc("""
			header.add('Baz','Bar');
			CONTINUE""");
		i.init(router);

		assertEquals(CONTINUE, i.handleRequest(exc));
		assertEquals("Bar", exc.getRequest().getHeader().getFirstValue("Baz"));
	}

	@Test
	public void testRequestObj() throws Exception {

		exc.getRequest().setBodyContent("ABC".getBytes());

		GroovyInterceptor i = new GroovyInterceptor();
		i.setSrc("""
				print message.bodyAsStringDecoded
				header.add('body',message.bodyAsStringDecoded);
				CONTINUE""");
		i.init(router);

		assertEquals(CONTINUE, i.handleRequest(exc));
		assertEquals("ABC", exc.getRequest().getHeader().getFirstValue("body"));
	}

	@Test
	public void testProperties() throws Exception {

		exc.getProperties().put("answer","42");

		GroovyInterceptor i = new GroovyInterceptor();
		i.setSrc("""
				print properties['answer']
				properties['thing']='towel'
				header.add('answer',properties['answer']);
				CONTINUE""");
		i.init(router);

		assertEquals(CONTINUE, i.handleRequest(exc));
		assertEquals("42", exc.getRequest().getHeader().getFirstValue("answer"));
		assertEquals("towel", exc.getProperty("thing"));
	}

	@Test
	public void request() throws Exception {
		GroovyInterceptor i = new GroovyInterceptor();
		i.setSrc("new Request.Builder().body('EFG').build()");
		i.init(router);

		assertEquals(CONTINUE, i.handleRequest(exc));
		assertEquals("EFG", exc.getRequest().getBodyAsStringDecoded());
	}

	@Test
	public void response() throws Exception {
		GroovyInterceptor i = new GroovyInterceptor();
		i.setSrc("Response.ok().body('ABCD').build()");
		i.init(router);

		assertEquals(RETURN, i.handleRequest(exc));
		assertEquals("ABCD", exc.getResponse().getBodyAsStringDecoded());
	}

	@Test
	public void returnMap() throws Exception {
		GroovyInterceptor i = new GroovyInterceptor();
		i.setSrc("""
				[id:7, name: 'Roller', desc: 'äöüÄÖÜ']""");
		i.init(router);

		assertEquals(CONTINUE, i.handleResponse(exc));
		assertTrue(exc.getResponse().isJSON());
		Map<String,Object> m = om.readValue(exc.getResponse().getBodyAsStringDecoded(),Map.class);
		assertEquals("Roller", m.get("name"));
		assertEquals("äöüÄÖÜ", m.get("desc"));
	}

	@Test
	public void jsonRequest() throws Exception {

		exc.getRequest().setBodyContent("""
				{"id":7,"city":"Bonn"}""".getBytes());
		exc.getRequest().getHeader().setContentType(APPLICATION_JSON);

		GroovyInterceptor i = new GroovyInterceptor();
		i.setSrc("""
				print json
				print json['id']
				header.add('id','id-'+json['id']);
				header.add('city',json.city);""");
		i.init(router);

		assertEquals(CONTINUE, i.handleRequest(exc));
		assertEquals("id-7", exc.getRequest().getHeader().getFirstValue("id"));
		assertEquals("Bonn", exc.getRequest().getHeader().getFirstValue("city"));
	}
}
