package com.predic8.membrane.servlet.test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

public class Basic {

	// integration test settings (corresponding to those in pom.xml)
	@SuppressWarnings("unused")
	private static final String HOST = "localhost";
	@SuppressWarnings("unused")
	private static final int PORT = 3021;
	// integration test settings (corresponding to those in proxies.xml)
	private static final String MEMBRANE_ADMIN_HOST = "localhost";
	private static final int MEMBRANE_ADMIN_PORT = 9000;
	private static final String BASIC_AUTH_USER = "admin";
	private static final String BASIC_AUTH_PASSWORD = "membrane";
	
	@Test
	public void testAdminConsoleReachable() throws ClientProtocolException, IOException {
		DefaultHttpClient hc = getAuthenticatingHttpClient();
		HttpGet g = new HttpGet(getBaseURL() + "admin/");
		HttpResponse r = hc.execute(g);
		assertSuccess(g, r);
		assertTrue(EntityUtils.toString(r.getEntity()).contains("ServiceProxies"));
	}

	@Test
	public void testAdminConsoleJavascriptDownloadable() throws ClientProtocolException, IOException {
		DefaultHttpClient hc = getAuthenticatingHttpClient();
		HttpGet g = new HttpGet(getBaseURL() + "jquery-ui/js/jquery-ui-1.8.13.custom.min.js");
		HttpResponse r = hc.execute(g);
		assertSuccess(g, r);
		assertTrue(EntityUtils.toString(r.getEntity()).contains("jQuery"));
	}

	private String getBaseURL() {
		return "http://" + MEMBRANE_ADMIN_HOST + ":" + MEMBRANE_ADMIN_PORT + "/";
	}
	
	private void assertSuccess(HttpRequestBase request, HttpResponse r) throws IOException {
		if (r.getStatusLine().getStatusCode() != 200) {
			throw new HttpResponseException(r.getStatusLine().getStatusCode(), r.getStatusLine().getReasonPhrase());
		}
	}

	
	private DefaultHttpClient getAuthenticatingHttpClient() {
		DefaultHttpClient hc = new DefaultHttpClient();
		HttpRequestInterceptor preemptiveAuth = new HttpRequestInterceptor() {
			public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
				AuthState authState = (AuthState) context.getAttribute(ClientContext.TARGET_AUTH_STATE);
				CredentialsProvider credsProvider = (CredentialsProvider) context.getAttribute(ClientContext.CREDS_PROVIDER);
				HttpHost targetHost = (HttpHost) context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
				if (authState.getAuthScheme() == null) {
					AuthScope authScope = new AuthScope(targetHost.getHostName(), targetHost.getPort());
					Credentials creds = credsProvider.getCredentials(authScope);
					if (creds != null) {
						authState.setAuthScheme(new BasicScheme());
						authState.setCredentials(creds);
					}
				}
			}    
		};
		hc.addRequestInterceptor(preemptiveAuth, 0);
		Credentials defaultcreds = new UsernamePasswordCredentials(BASIC_AUTH_USER, BASIC_AUTH_PASSWORD);
		BasicCredentialsProvider bcp = new BasicCredentialsProvider();
		bcp.setCredentials(new AuthScope(MEMBRANE_ADMIN_HOST, MEMBRANE_ADMIN_PORT, AuthScope.ANY_REALM), defaultcreds);
		hc.setCredentialsProvider(bcp);
		hc.setCookieStore(new BasicCookieStore());
		return hc;
	}

}
