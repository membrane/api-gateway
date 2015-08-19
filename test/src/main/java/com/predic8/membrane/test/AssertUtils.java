/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.test;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
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
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
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

	public static String getAndAssert200(String url, String[] header) throws ParseException, IOException {
		return getAndAssert(200, url, header);
	}

	public static String getAndAssert(int expectedHttpStatusCode, String url) throws ParseException, IOException {
		return getAndAssert(expectedHttpStatusCode, url, null);
	}

	public static String getAndAssert(int expectedHttpStatusCode, String url, String[] header) throws ParseException, IOException {
		if (hc == null)
			hc = new DefaultHttpClient();
		HttpGet get = new HttpGet(url);
		try {
			if (header != null)
				for (int i = 0; i < header.length; i += 2)
					get.addHeader(header[i], header[i+1]);
			HttpResponse res = hc.execute(get);
			try {
				assertEquals(expectedHttpStatusCode, res.getStatusLine().getStatusCode());
			} catch (AssertionError e) {
				throw new AssertionError(e.getMessage() + " while fetching " + url);
			}
			HttpEntity entity = res.getEntity();
			return entity == null ? "" : EntityUtils.toString(entity);
		} finally {
			get.releaseConnection();
		}
	}

	public static String postAndAssert200(String url, String body) throws ClientProtocolException, IOException {
		return postAndAssert(200, url, body);
	}

	public static String assertStatusCode(int expectedHttpStatusCode, HttpUriRequest request) throws ClientProtocolException, IOException {
		if (hc == null)
			hc = new DefaultHttpClient();
		HttpResponse res = hc.execute(request);
		assertEquals(expectedHttpStatusCode, res.getStatusLine().getStatusCode());
		return EntityUtils.toString(res.getEntity());
	}

	public static String postAndAssert(int expectedHttpStatusCode, String url, String body) throws ClientProtocolException, IOException {
		return postAndAssert(expectedHttpStatusCode, url, new String[0], body);
	}

	public static String postAndAssert(int expectedHttpStatusCode, String url, String[] headers, String body) throws ClientProtocolException, IOException {
		if (hc == null)
			hc = new DefaultHttpClient();
		HttpPost post = new HttpPost(url);
		for (int i = 0; i < headers.length; i+=2)
			post.setHeader(headers[i], headers[i+1]);
		post.setEntity(new StringEntity(body));
		try {
			HttpResponse res = hc.execute(post);
			assertEquals(expectedHttpStatusCode, res.getStatusLine().getStatusCode());
			return EntityUtils.toString(res.getEntity());
		} finally {
			post.releaseConnection();
		}
	}

	public static void disableHTTPAuthentication() {
		hc = new DefaultHttpClient();
	}

	public static void setupHTTPAuthentication(String host, int port, String user, String pass) {
		if (hc != null)
			closeConnections();
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
						authState.update(new BasicScheme(), creds);
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

	public static void trustAnyHTTPSServer(int port) throws NoSuchAlgorithmException, KeyManagementException {
		SSLContext context = SSLContext.getInstance("SSL");
		context.init(null, new TrustManager[] { new X509TrustManager() {
			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			@Override
			public void checkServerTrusted(X509Certificate[] arg0, String arg1)
					throws CertificateException {
			}

			@Override
			public void checkClientTrusted(X509Certificate[] arg0, String arg1)
					throws CertificateException {
			}
		} }, new SecureRandom());

		SSLSocketFactory sslsf = new SSLSocketFactory(context, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		Scheme scheme = new Scheme("https", port, sslsf);
		if (hc == null)
			hc = new DefaultHttpClient();
		hc.getConnectionManager().getSchemeRegistry().register(scheme);
	}

	public static void replaceInFile(File file, String from, String to_) throws IOException {
		FileUtils.writeStringToFile(file, FileUtils.readFileToString(file).replace(from, to_));

	}

	public static void closeConnections() {
		if (hc != null)
			hc.getConnectionManager().closeIdleConnections(0, TimeUnit.SECONDS);
	}

}
