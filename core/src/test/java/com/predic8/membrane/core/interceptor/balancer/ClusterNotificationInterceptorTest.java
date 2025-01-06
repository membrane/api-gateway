/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.proxies.*;
import org.apache.commons.codec.binary.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.net.*;
import java.security.*;

import static com.predic8.membrane.core.util.URLParamUtil.*;
import static java.nio.charset.StandardCharsets.*;
import static javax.crypto.Cipher.ENCRYPT_MODE;
import static org.apache.commons.codec.binary.Base64.encodeBase64;
import static org.junit.jupiter.api.Assertions.*;

public class ClusterNotificationInterceptorTest {
	private HttpRouter router;
	private ClusterNotificationInterceptor interceptor;

	@BeforeEach
	public void setUp() throws Exception {
		ServiceProxy proxy = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3002), "thomas-bayer.com", 80);
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(proxy);

		interceptor = new ClusterNotificationInterceptor();
		router.addUserFeatureInterceptor(interceptor);

		LoadBalancingInterceptor lbi = new LoadBalancingInterceptor();
		lbi.setName("Default");
		ServiceProxy proxy2 = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3003), "thomas-bayer.com", 80);
		router.getRuleManager().addProxyAndOpenPortIfNew(proxy2);
		proxy2.getInterceptors().add(lbi);
		router.init();
	}

	@AfterEach
	public void tearDown() {
		router.shutdown();
	}

	@Test
	public void testUpEndpoint() throws Exception {
		GetMethod get = new GetMethod("http://localhost:3002/clustermanager/up?"+
				createQueryString("host", "node1.clustera",
						"port", "3018",
						"cluster","c1"));

		assertEquals(204, new HttpClient().executeMethod(get));
		assertEquals("node1.clustera", BalancerUtil.lookupBalancer(router, "Default").getAllNodesByCluster("c1").getFirst().getHost());

	}

	@Test
	public void testTakeOutEndpoint() throws Exception {
		GetMethod get = new GetMethod("http://localhost:3002/clustermanager/takeout?"+
				createQueryString("host", "node1.clustera",
						"port", "3018",
						"cluster","c1"));

		assertEquals(204, new HttpClient().executeMethod(get));
		assertEquals(1, BalancerUtil.lookupBalancer(router, "Default").getAllNodesByCluster("c1").size());
		assertTrue(BalancerUtil.lookupBalancer(router, "Default").getAllNodesByCluster("c1").getFirst().isTakeOut());
	}

	@Test
	public void testDownEndpoint() throws Exception {
		GetMethod get = new GetMethod("http://localhost:3002/clustermanager/down?"+
				createQueryString("host", "node1.clustera",
						"port", "3018",
						"cluster","c1"));

		assertEquals(204, new HttpClient().executeMethod(get));
		assertEquals(1, BalancerUtil.lookupBalancer(router, "Default").getAllNodesByCluster("c1").size());
        assertFalse(BalancerUtil.lookupBalancer(router, "Default").getAllNodesByCluster("c1").getFirst().isUp());
	}

	@Test
	public void testDefaultCluster() throws Exception {
		GetMethod get = new GetMethod("http://localhost:3002/clustermanager/up?"+
				createQueryString("host", "node1.clustera",
						"port", "3018"));

		assertEquals(204, new HttpClient().executeMethod(get));
		assertEquals(1, BalancerUtil.lookupBalancer(router, "Default").getAllNodesByCluster("Default").size());
		assertEquals("node1.clustera", BalancerUtil.lookupBalancer(router, "Default").getAllNodesByCluster("Default").getFirst().getHost());
	}

	@Test
	public void testSecurity() throws Exception {
		interceptor.setValidateSignature(true);
		interceptor.setKeyHex("6f488a642b740fb70c5250987a284dc0");

		assertEquals(204, new HttpClient().executeMethod(getSecurityTestMethod(5004)));

		interceptor.setTimeout(3018);
		GetMethod get = getSecurityTestMethod(System.currentTimeMillis());
		assertEquals(204, new HttpClient().executeMethod(get));
		assertEquals(204, new HttpClient().executeMethod(get));

		Thread.sleep(6000);
		assertEquals(403, new HttpClient().executeMethod(get));

	}

	private GetMethod getSecurityTestMethod(long time) throws Exception {
        return new GetMethod("http://localhost:3002/clustermanager/up?data=" +
				URLEncoder.encode(getEncryptedQueryString(getqParams(time)), UTF_8));
	}

	private static @NotNull String getqParams(long time) {
		return "cluster=c3&host=node1.clustera&port=3018&time=" + time + "&nonce=" + new SecureRandom().nextLong();
	}

	private String getEncryptedQueryString(String qParams) throws Exception {
		Cipher cipher = Cipher.getInstance("AES");

		cipher.init(ENCRYPT_MODE, new SecretKeySpec(Hex.decodeHex("6f488a642b740fb70c5250987a284dc0".toCharArray()), "AES"));
		return new String(encodeBase64(cipher.doFinal(qParams.getBytes(UTF_8))), UTF_8);
	}
}
