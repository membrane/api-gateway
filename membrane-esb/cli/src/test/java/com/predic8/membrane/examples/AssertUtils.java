package com.predic8.membrane.examples;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

public class AssertUtils {
	
	public static void assertContains(String needle, String haystack) {
		if (haystack.contains(needle))
			return;
		throw new AssertionError("The string '" + haystack + "' does not contain '" + needle + "'.");
	}

	public static void assertContainsNot(String needle, String haystack) {
		if (!haystack.contains(needle))
			return;
		throw new AssertionError("The string '" + haystack + "' does contain '" + needle + "'.");
	}
	
	private static HttpClient hc;
	
	public static String getAndAssert200(String url) throws ParseException, IOException {
		return getAndAssert(200, url);
	}
	
	public static String getAndAssert(int expectedHttpStatusCode, String url) throws ParseException, IOException {
		if (hc == null)
			hc = new DefaultHttpClient();
		HttpResponse res = hc.execute(new HttpGet(url));
		assertEquals(expectedHttpStatusCode, res.getStatusLine().getStatusCode());
		return EntityUtils.toString(res.getEntity());
	}

	public static String postAndAssert200(String url, String body) throws ClientProtocolException, IOException {
		return postAndAssert(200, url, body);
	}

	public static String postAndAssert(int expectedHttpStatusCode, String url, String body) throws ClientProtocolException, IOException {
		if (hc == null)
			hc = new DefaultHttpClient();
		HttpPost post = new HttpPost(url);
		post.setEntity(new StringEntity(body));
		HttpResponse res = hc.execute(post);
		assertEquals(expectedHttpStatusCode, res.getStatusLine().getStatusCode());
		return EntityUtils.toString(res.getEntity());
	}
	
	public static void setupHTTPAuthentication(String host, int port, String user, String pass) {
		hc = getAuthenticatingHttpClient(host, port, user, pass);
	}
	
	private static DefaultHttpClient getAuthenticatingHttpClient(String host, int port, String user, String pass) {
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
		Credentials defaultcreds = new UsernamePasswordCredentials(user, pass);
		BasicCredentialsProvider bcp = new BasicCredentialsProvider();
		bcp.setCredentials(new AuthScope(host, port, AuthScope.ANY_REALM), defaultcreds);
		hc.setCredentialsProvider(bcp);
		hc.setCookieStore(new BasicCookieStore());
		return hc;
	}

}
