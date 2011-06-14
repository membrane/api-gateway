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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.*;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

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
	public void testSecurity() throws Exception {
		interceptor.setValidateSignature(true);
		interceptor.setKeyBase64("b0iKZCt0D7cMUlCYeihNwA==");
		
		assertEquals(204, new HttpClient().executeMethod(getSecurityTestMethod(5004)));
		
		interceptor.setTimeout(5000);
		GetMethod get = getSecurityTestMethod(System.currentTimeMillis());
		assertEquals(204, new HttpClient().executeMethod(get));
		assertEquals(204, new HttpClient().executeMethod(get));

		Thread.sleep(6000);
		assertEquals(403, new HttpClient().executeMethod(get));
		
	}

	private GetMethod getSecurityTestMethod(long time) throws Exception {	
		String qParams = "cluster=c3&host=node1.clustera&port=5000&time=" + time + "&nonce=" + new SecureRandom().nextLong();
		return new GetMethod("http://localhost:8000/clustermanager/up?data=" + 
				   URLEncoder.encode(getEncryptedQueryString(qParams),"UTF-8"));
	}

	private String getEncryptedQueryString(String qParams) throws Exception {
		Cipher cipher = Cipher.getInstance("AES");
		
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Base64.decodeBase64("b0iKZCt0D7cMUlCYeihNwA==".getBytes("UTF-8")), "AES"));
		return new String(Base64.encodeBase64(cipher.doFinal(qParams.getBytes("UTF-8"))),"UTF-8");
	}	
}
