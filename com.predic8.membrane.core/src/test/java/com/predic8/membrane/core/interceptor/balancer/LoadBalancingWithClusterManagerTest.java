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
package com.predic8.membrane.core.interceptor.balancer;

import static com.predic8.membrane.core.util.URLParamUtil.createQueryString;
import static junit.framework.Assert.assertEquals;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.http.params.HttpProtocolParams;
import org.junit.After;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.services.DummyWebServiceInterceptor;

public class LoadBalancingWithClusterManagerTest {

	private HttpRouter lb;
	private HttpRouter node1;
	private HttpRouter node2;
	private HttpRouter node3;

	@Test 
	public void nodesTest() throws Exception {
		node1 = new HttpRouter();
		node2 = new HttpRouter();
		node3 = new HttpRouter();

		DummyWebServiceInterceptor service1 = startNode(node1, 2000);
		DummyWebServiceInterceptor service2 = startNode(node2, 3000);
		DummyWebServiceInterceptor service3 = startNode(node3, 4000);
		
		startLB();
		
		sendNotification("up", 2000);
		sendNotification("up", 3000);
				
		assertEquals(200, post("/getBankwithSession555555.xml"));// goes to service one
		assertEquals(1, service1.counter);
		assertEquals(0, service2.counter);

		assertEquals(200, post("/getBankwithSession555555.xml"));// goes to service 1 again
		assertEquals(2, service1.counter);
		assertEquals(0, service2.counter);

		assertEquals(200, post("/getBankwithSession444444.xml")); // goes to service 2
		assertEquals(2, service1.counter);
		assertEquals(1, service2.counter);
		
		sendNotification("down", 2000);

		assertEquals(200, post("/getBankwithSession555555.xml")); // goes to service 2 because service 1 is down
		assertEquals(2, service1.counter);
		assertEquals(2, service2.counter);

		sendNotification("up", 4000);

		assertEquals(0, service3.counter);
		assertEquals(200, post("/getBankwithSession666666.xml")); // goes to service 3
		assertEquals(2, service1.counter);
		assertEquals(2, service2.counter);
		assertEquals(1, service3.counter);
		
		assertEquals(200, post("/getBankwithSession555555.xml")); // goes to service 2
		assertEquals(200, post("/getBankwithSession444444.xml")); // goes to service 2
		assertEquals(200, post("/getBankwithSession666666.xml")); // goes to service 3
		assertEquals(2, service1.counter);
		assertEquals(4, service2.counter);
		assertEquals(2, service3.counter);
		
		
	}

	@After
	public void tearDown() throws Exception {
		lb.getTransport().closeAll();
		node1.getTransport().closeAll();
		node2.getTransport().closeAll();
		node3.getTransport().closeAll();
	}
	
	private void startLB() throws Exception {

		LoadBalancingInterceptor lbi = new LoadBalancingInterceptor();
		lbi.setName("Default");
		XMLElementSessionIdExtractor extractor = new XMLElementSessionIdExtractor();
		extractor.setLocalName("session");
		extractor.setNamespace("http://predic8.com/session/");
		lbi.setSessionIdExtractor(extractor);

		ServiceProxy lbiRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3017), "thomas-bayer.com", 80);
		lbiRule.getInterceptors().add(lbi);
		
		ClusterNotificationInterceptor cni = new ClusterNotificationInterceptor();
		
		ServiceProxy cniRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3012), "thomas-bayer.com", 80);
		cniRule.getInterceptors().add(cni);
		
		lb = new HttpRouter();
		lbi.setRouter(lb);
		cni.setRouter(lb);
		lb.getRuleManager().addRuleIfNew(lbiRule);
		lb.getRuleManager().addRuleIfNew(cniRule);
	}

	private DummyWebServiceInterceptor startNode(HttpRouter node, int port) throws IOException {
		DummyWebServiceInterceptor service1 = new DummyWebServiceInterceptor();
		node.getTransport().getInterceptors().add(service1);
		node.getRuleManager().addRuleIfNew(new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", port), "thomas-bayer.com", 80));
		return service1;
	}

	private HttpClient getClient() {
		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		return client;
	}

	private PostMethod getPostMethod(String request) {
		PostMethod post = new PostMethod("http://localhost:3017/axis2/services/BLZService");
		post.setRequestEntity(new InputStreamRequestEntity(this.getClass().getResourceAsStream(request)));
		post.setRequestHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
		post.setRequestHeader(Header.SOAP_ACTION, "");

		return post;
	}
	
	private void sendNotification(String cmd, int port) throws UnsupportedEncodingException, IOException,
			HttpException {
		PostMethod post = new PostMethod("http://localhost:3012/clustermanager/"+cmd+"?"+
				   createQueryString("host", "localhost",
						   			 "port", String.valueOf(port)));
		new HttpClient().executeMethod(post);
	}
	
	private int post(String req) throws IOException, HttpException {
		return getClient().executeMethod(getPostMethod(req));
	}
	
}
