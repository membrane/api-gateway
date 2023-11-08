package com.predic8.membrane.integration;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.interceptor.acl.*;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

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
		initRouter(getHostnameResource("localhost"));
		assertEquals(200, getClient().executeMethod(GET));
	}

	@Test
	public void notMatchesHostname() {
	}

	@Test
	public void matchesBlobIp() throws Exception {
		initRouter(getIpResource("127.0.0.*", ParseType.GLOB));
		assertEquals(200, getClient().executeMethod(GET));
	}

	@Test
	public void notMatchesBlobIp() {
	}

	@Test
	public void matchesRegexIp() {
	}

	@Test
	public void notMatchesRegexIp() {
	}

	@Test
	public void matchesCidrIp() {
	}

	@Test
	public void notMatchesCidrIp() {
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
			setFile("src/test/resources/acl/acl.xml");
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
