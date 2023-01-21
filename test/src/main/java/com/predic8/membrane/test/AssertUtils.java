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

import java.io.File;
import java.io.IOException;
import java.nio.charset.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

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
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.util.EntityUtils;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.FileUtils.writeStringToFile;
import static org.apache.http.client.protocol.HttpClientContext.CREDS_PROVIDER;
import static org.apache.http.client.protocol.HttpClientContext.TARGET_AUTH_STATE;
import static org.apache.http.protocol.HttpCoreContext.HTTP_TARGET_HOST;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
		HttpResponse result;
		if (hc == null)
			hc = HttpClientBuilder.create().build();
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

	public static HttpResponse getAndAssertWithResponse(int expectedHttpStatusCode, String url, String[] header) throws ParseException, IOException {
		if (hc == null)
			hc = HttpClientBuilder.create().build();
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
			System.out.println(res);
			res.getEntity();
			return res;
		} finally {
			get.releaseConnection();
		}
	}

	public static String postAndAssert200(String url, String body) throws IOException {
		return postAndAssert(200, url, body);
	}

	public static String assertStatusCode(int expectedHttpStatusCode, HttpUriRequest request) throws IOException {
		if (hc == null)
			hc = HttpClientBuilder.create().build();
		HttpResponse res = hc.execute(request);
		assertEquals(expectedHttpStatusCode, res.getStatusLine().getStatusCode());
		return EntityUtils.toString(res.getEntity());
	}

	public static String postAndAssert(int expectedHttpStatusCode, String url, String body) throws IOException {
		return postAndAssert(expectedHttpStatusCode, url, new String[0], body);
	}

	public static String postAndAssert(int expectedHttpStatusCode, String url, String[] headers, String body) throws IOException {
		return invokeAndAssert(new HttpPost(url),expectedHttpStatusCode,url,headers,body);
	}

	public static String putAndAssert(int expectedHttpStatusCode, String url, String[] headers, String body) throws IOException {
		return invokeAndAssert(new HttpPut(url),expectedHttpStatusCode,url,headers,body);
	}

	public static String invokeAndAssert(HttpEntityEnclosingRequestBase requestBase, int expectedHttpStatusCode, String url, String[] headers, String body) throws IOException {
		if (hc == null)
			hc = HttpClientBuilder.create().build();
		for (int i = 0; i < headers.length; i+=2)
			requestBase.setHeader(headers[i], headers[i+1]);
		requestBase.setEntity(new StringEntity(body));
		try {
			HttpResponse res = hc.execute(requestBase);
			assertEquals(expectedHttpStatusCode, res.getStatusLine().getStatusCode());
			return EntityUtils.toString(res.getEntity());
		} finally {
			requestBase.releaseConnection();
		}
	}

	public static void disableHTTPAuthentication() {
		hc = HttpClientBuilder.create().build();
	}

	public static void setupHTTPAuthentication(String host, int port, String user, String pass) {
		if (hc != null)
			closeConnections();
		hc = getAuthenticatingHttpClient(host, port, user, pass);
	}

	private static HttpClient getAuthenticatingHttpClient(String host, int port, String user, String pass) {
		Credentials defaultcreds = new UsernamePasswordCredentials(user, pass);
		BasicCredentialsProvider bcp = new BasicCredentialsProvider();
		bcp.setCredentials(new AuthScope(host, port, AuthScope.ANY_REALM), defaultcreds);
		HttpRequestInterceptor preemptiveAuth = (request, context) -> {
			AuthState authState = (AuthState) context.getAttribute(TARGET_AUTH_STATE);
			if (authState.getAuthScheme() == null) {
				Credentials creds = getCredentials(context);
				if (creds != null) {
					authState.update(new BasicScheme(), creds);
				}
			}
		};
		return HttpClientBuilder.create()
				.setDefaultCookieStore(new BasicCookieStore())
				.setDefaultCredentialsProvider(bcp)
				.addInterceptorFirst(preemptiveAuth)
				.build();
	}

	private static Credentials getCredentials(HttpContext context) {
		return ((CredentialsProvider) context.getAttribute(CREDS_PROVIDER)).getCredentials(getAuthScope((HttpHost) context.getAttribute(HTTP_TARGET_HOST)));
	}

	private static AuthScope getAuthScope(HttpHost targetHost) {
		return new AuthScope(targetHost.getHostName(), targetHost.getPort());
	}

	public static void trustAnyHTTPSServer(int port) throws NoSuchAlgorithmException, KeyManagementException {
		SSLContext context = SSLContext.getInstance("SSL");
		context.init(null, new TrustManager[] { new X509TrustManager() {
			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			@Override
			public void checkServerTrusted(X509Certificate[] arg0, String arg1) {
			}

			@Override
			public void checkClientTrusted(X509Certificate[] arg0, String arg1) {
			}
		} }, new SecureRandom());

		hc = HttpClientBuilder.create().setSSLSocketFactory(new SSLConnectionSocketFactory(context, NoopHostnameVerifier.INSTANCE)).build();
	}

	public static void replaceInFile(File file, String from, String to_) throws IOException {
		writeStringToFile(file, FileUtils.readFileToString(file, UTF_8).replace(from, to_), UTF_8);

	}

	public static void closeConnections() {
		if (hc != null) {
			HttpClientUtils.closeQuietly(hc);
			hc = null;
		}
	}
}
