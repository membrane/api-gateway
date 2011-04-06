/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.integration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import junit.framework.TestCase;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingInterceptor;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.ProxyRuleKey;


public class ProxyRuleTest extends TestCase {

	private Router router;
	
	private static ProxyRule rule1;
	
	private static byte[] buffer;
	
	@Before
	public void setUp() throws Exception {
		router = Router.init("resources/proxy-rules-test-monitor-beans.xml");
		router.getRuleManager().addRuleIfNew(new ProxyRule(new ProxyRuleKey(3128)));
	}
	
	@Override
	protected void tearDown() throws Exception {
		router.getTransport().closeAll();
	}
	
	@Test
	public void testPost() throws Exception {
		HttpClient client = new HttpClient();
		GetMethod get = new GetMethod("https://predic8.com");
		assertEquals(200, client.executeMethod(get));
	}
	
	public void testWriteRuleToByteBuffer() throws Exception {
		rule1 = new ProxyRule(new ProxyRuleKey(8888));
		rule1.setName("Rule 1");
		rule1.setInboundTLS(true);
		rule1.setBlockResponse(true);
		rule1.setInterceptors(getInterceptors());
		
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(os, Constants.ENCODING_UTF_8);
		rule1.write(writer);
		writer.flush();
		buffer = os.toByteArray();
	}
	
	public void testReadRuleFromByteBuffer() throws Exception {
		ProxyRule rule2 = new ProxyRule();
		rule2.setRouter(router);
		
		XMLInputFactory factory = XMLInputFactory.newInstance();
		XMLStreamReader reader = factory.createXMLStreamReader((new ByteArrayInputStream(buffer)), Constants.ENCODING_UTF_8);
		
		while(reader.next() != XMLStreamReader.START_ELEMENT);
		
		rule2.parse(reader);
		
		assertEquals(8888, rule2.getKey().getPort());
		assertEquals("Rule 1", rule2.getName());
		assertNull(rule2.getLocalHost()); 
		assertEquals(true, rule2.isInboundTLS());
		assertFalse(rule2.isOutboundTLS());
		
		List<Interceptor> inters = rule2.getInterceptors();
		assertFalse(inters.isEmpty());
		assertTrue(inters.size()  == 2);
		inters.get(0).getId().equals("roundRobinBalancer");
		inters.get(1).getId().equals("accessControlInterceptor");
		
		assertEquals(true, rule2.isBlockResponse());
		assertFalse(rule2.isBlockRequest());
	}
	
	private List<Interceptor> getInterceptors() {
		List<Interceptor> interceptors = new ArrayList<Interceptor>();
		Interceptor balancer = new LoadBalancingInterceptor();
		balancer.setId("roundRobinBalancer");
		interceptors.add(balancer);
		
		Interceptor acl = new AccessControlInterceptor();
		acl.setId("accessControlInterceptor");
		interceptors.add(acl);
		return interceptors;
	}
	
}
