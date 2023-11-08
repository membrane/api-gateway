package com.predic8.membrane.integration;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.interceptor.acl.*;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.predic8.membrane.core.interceptor.acl.ParseType.*;
import static java.util.regex.Pattern.compile;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AccessControlInterceptorIntegrationTest {

	private static HttpRouter router;

	private static final GetMethod GET = new GetMethod("http://localhost:5005");

	@BeforeEach
	public void setUp() throws Exception {
		Rule rule = new ServiceProxy(new ServiceProxyKey("localhost", "GET", ".*", 5005), "www.google.de", 80);
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(rule);
	}

	@AfterEach
	public void tearDown() throws Exception {
		router.shutdown();
	}

	@Test
	public void matchesHostname() throws Exception {
		initRouter(getHostnameResource("local.*"));
		assertEquals(200, getClient().executeMethod(GET));
	}

	@Test
	public void notMatchesHostname() throws Exception {
		initRouter(getHostnameResource("hostlocal"));
		assertEquals(401, getClient().executeMethod(GET));
	}

	@Test
	public void matchesGlobIp() throws Exception {
		initRouter(getIpResource("127.0.0.*", GLOB));
		assertEquals(200, getClient().executeMethod(GET));
	}

	@Test
	public void notMatchesGlobIp() throws Exception {
		initRouter(getIpResource("127.0.1.*", GLOB));
		assertEquals(401, getClient().executeMethod(GET));
	}

	@Test
	public void matchesRegexIp() throws Exception {
		initRouter(getIpResource("127.0.0.(2|1)", REGEX));
		assertEquals(200, getClient().executeMethod(GET));
	}

	@Test
	public void notMatchesRegexIp() throws Exception {
		initRouter(getIpResource("127.0.0.\\s", REGEX));
		assertEquals(401, getClient().executeMethod(GET));
	}

	@Test
	public void matchesCidrIp() throws Exception {
		initRouter(getIpResource("127.0.0.0/20", CIDR));
		assertEquals(200, getClient().executeMethod(GET));
	}

	@Test
	public void notMatchesCidrIp() throws Exception {
		initRouter(getIpResource("127.0.0.0/32", CIDR));
		assertEquals(401, getClient().executeMethod(GET));
	}

	private void initRouter(Resource r) throws Exception {
		router.addUserFeatureInterceptor(buildAci(r));
		router.init();
	}

	private AccessControlInterceptor buildAci(Resource r) {
		return new AccessControlInterceptorManualProxy(){{
			setAccessControl(
					new AccessControl(router) {{
						addResource(r);
					}}
			);
		}};
	}

	private Resource getIpResource(String scheme, ParseType ptype) {
		return getResource(new Ip(router) {{
			setParseType(ptype);
			setSchema(scheme);
		}});
	}

	private Resource getHostnameResource(String scheme) {
		return getResource(new Hostname(router) {{
			setSchema(scheme);
		}});
	}

	private Resource getResource(AbstractClientAddress addr) {
		return new Resource(router) {{
			addAddress(addr);
			setUriPattern(compile(".*"));
		}};
	}

	private HttpClient getClient() throws UnknownHostException {
		HttpClient client = new HttpClient();
		HostConfiguration config = new HostConfiguration();
		config.setLocalAddress(InetAddress.getByAddress(new byte[]{ (byte)127, (byte)0, (byte)0,  (byte)1 }));
		client.setHostConfiguration(config);
		return client;
	}
}
