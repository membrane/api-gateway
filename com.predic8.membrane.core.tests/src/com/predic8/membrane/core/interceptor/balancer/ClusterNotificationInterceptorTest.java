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
package com.predic8.membrane.core.interceptor.balancer;

import static com.predic8.membrane.core.util.URLUtil.createQueryString;

import java.io.*;
import java.net.URLEncoder;
import java.security.*;

import junit.framework.TestCase;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.*;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.rules.Rule;

public class ClusterNotificationInterceptorTest extends TestCase {
	
	static private ClusterManager clusterManager  = new ClusterManager();
	private HttpRouter router;
	private ClusterNotificationInterceptor interceptor;

	@Before
	public void setUp() throws Exception {
		Rule rule = new ForwardingRule(new ForwardingRuleKey("localhost", "*", ".*", 8000), "thomas-bayer.com", 80);
		router = new HttpRouter();
		router.getRuleManager().addRuleIfNew(rule);
		router.setClusterManager(clusterManager);
		
		interceptor = new ClusterNotificationInterceptor();
		interceptor.setRouter(router);
		router.getTransport().getInterceptors().add(interceptor);

	}
	
	@After
	public void tearDown() throws Exception {
		router.getTransport().closeAll();
	}
	
	@Test
	public void testUpEndpoint() throws Exception {
		GetMethod get = new GetMethod("http://localhost:8000/clustermanager/up?"+
									   createQueryString("host", "node1.clustera",
									   			 		 "port", "5000", 
										  			     "cluster","c1"));
		
		assertEquals(204, new HttpClient().executeMethod(get));
		assertEquals("node1.clustera", clusterManager.getAllNodes("c1").get(0).getHost());
		
	}	
	
	@Test
	public void testDownEndpoint() throws Exception {
		GetMethod get = new GetMethod("http://localhost:8000/clustermanager/down?"+
									   createQueryString("host", "node1.clustera",
											   			 "port", "5000",
										  			     "cluster","c1"));
		
		assertEquals(204, new HttpClient().executeMethod(get));
		assertEquals(1, clusterManager.getAllNodes("c1").size());
		assertEquals(false, clusterManager.getAllNodes("c1").get(0).isUp());		
	}	

	@Test
	public void testDefaultCluster() throws Exception {
		GetMethod get = new GetMethod("http://localhost:8000/clustermanager/up?"+
									   createQueryString("host", "node1.clustera",
									   			 		 "port", "5000"));
		
		assertEquals(204, new HttpClient().executeMethod(get));
		assertEquals(1, clusterManager.getAllNodes("Default").size());
		assertEquals("node1.clustera", clusterManager.getAllNodes("Default").get(0).getHost());
	}	

	@Test
	public void testSignature() throws Exception {
		interceptor.setKeyStore(new File( new File("."), "resources/membrane.jks").getAbsolutePath());
		interceptor.setValidateSignature(true);
		
		assertEquals(403, new HttpClient().executeMethod(getWrongSignatureTestMethod()));
		
		assertEquals(204, new HttpClient().executeMethod(getSignatureTestMethod(5004)));
		
		interceptor.setTimeout(5000);
		GetMethod get = getSignatureTestMethod(System.currentTimeMillis());
		assertEquals(204, new HttpClient().executeMethod(get));
		assertEquals(204, new HttpClient().executeMethod(get));

		Thread.sleep(6000);
		assertEquals(403, new HttpClient().executeMethod(get));
		
	}

	private GetMethod getWrongSignatureTestMethod()
			throws UnsupportedEncodingException {
		return new GetMethod("http://localhost:8000/clustermanager/up?"+
									   createQueryString("host", "node1.clustera",
									   			 		 "port", "5000",
									   			 		 "cluster", "c1",
									   			 		 "time", "3433",
									   			 		 "signature", "MCwCFHNPunXJyY45ltGckunFxPDth9i0AhQFAbsgB7yPJdyYIL3zE3QXmP+F8A=="));
	}	

	private GetMethod getSignatureTestMethod(long time) throws Exception {		
		return new GetMethod("http://localhost:8000/clustermanager/up?cluster=c3&host=node1.clustera&port=5000&time=" + time +
			   "&signature=" + URLEncoder.encode(getSigBase64(time+"c3node1.clustera5000"),"UTF-8"));
	}

	private String getSigBase64(String data) throws Exception {
		Signature sig = Signature.getInstance("SHA1withDSA");
		sig.initSign(getPrivateKey());
		sig.update(data.getBytes("UTF-8"));		
		return new String(Base64.encodeBase64(sig.sign()));
	}

	private PrivateKey getPrivateKey() throws Exception {
		
		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(getClass().getResourceAsStream("/membrane.jks"), "secret".toCharArray());
		
		return ((KeyStore.PrivateKeyEntry) ks.getEntry("membrane", new KeyStore.PasswordProtection("secret".toCharArray()))).getPrivateKey();
	}
	
}
