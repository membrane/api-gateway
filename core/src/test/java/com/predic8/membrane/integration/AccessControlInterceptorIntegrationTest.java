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
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AccessControlInterceptorIntegrationTest {

	private static HttpRouter router;

	@BeforeEach
	public void setUp() throws Exception {
		Rule rule = new ServiceProxy(new ServiceProxyKey("127.0.0.1", "POST", ".*", 3008), "www.google.de", 80);
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(rule);
	}

	@AfterEach
	public void tearDown() throws Exception {
		router.shutdown();
	}

	@Test
	public void matchesHostname() {
	}

	@Test
	public void notMatchesHostname() {
	}

	@Test
	public void matchesBlobIp() {
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
		return new AccessControlInterceptor(){{
			setAccessControl(
					new AccessControl(router) {{
						addResource(r);
					}}
			);
		}};
	}

	private Resource getIpResource(String scheme, ParseType ptype) {
		return new Resource(router) {{
			addAddress(new Ip(router) {{
				setParseType(ptype);
				setSchema(scheme);
			}});
		}};
	}

	private Resource getHostnameResource(String scheme) {
		return new Resource(router) {{
			addAddress(new Hostname(router) {{
				setSchema(scheme);
			}});
		}};
	}

	private HttpClient getClient(byte[] ip) throws UnknownHostException {
		HttpClient client = new HttpClient();
		HostConfiguration config = new HostConfiguration();
		config.setLocalAddress(InetAddress.getByAddress(ip));
		client.setHostConfiguration(config);
		return client;
	}
}
