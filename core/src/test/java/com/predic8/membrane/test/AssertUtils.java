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

import org.apache.commons.io.*;
import org.apache.http.*;
import org.apache.http.auth.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.*;
import org.apache.http.conn.ssl.*;
import org.apache.http.entity.*;
import org.apache.http.impl.auth.*;
import org.apache.http.impl.client.*;
import org.apache.http.protocol.*;
import org.apache.http.util.*;
import org.junit.jupiter.api.*;

import javax.net.ssl.*;
import java.io.*;
import java.security.*;
import java.security.cert.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.FileUtils.writeStringToFile;
import static org.apache.http.client.protocol.HttpClientContext.*;

public class AssertUtils implements AutoCloseable {

	private HttpClient hc = HttpClientBuilder.create().build();

	public String getAndAssert200(String url) throws ParseException, IOException {
		return getAndAssert(200, url);
	}

	public String getAndAssert200(String url, String[] header) throws ParseException, IOException {
		return getAndAssert(200, url, header);
	}

	public String getAndAssert(int expectedHttpStatusCode, String url) throws ParseException, IOException {
		return getAndAssert(expectedHttpStatusCode, url, null);
	}

	public String getAndAssert(int expectedHttpStatusCode, String url, String[] header) throws ParseException, IOException {
		HttpGet get = new HttpGet(url);
		try {
			HttpResponse res = invokeAndAssertInternal(expectedHttpStatusCode, url, header, get);
			HttpEntity entity = res.getEntity();
			return entity == null ? "" : EntityUtils.toString(entity);
		} finally {
			get.releaseConnection();
		}

	}

	public HttpResponse getAndAssertWithResponse(int expectedHttpStatusCode, String url, String[] header) throws ParseException, IOException {
		HttpGet get = new HttpGet(url);
		try {
			return invokeAndAssertInternal(expectedHttpStatusCode, url, header, get);
		} finally {
			get.releaseConnection();
		}
	}

	public HttpResponse invokeAndAssertInternal(int expectedHttpStatusCode, String url, String[] header, HttpGet get) throws IOException {
		if (header != null)
			for (int i = 0; i < header.length; i += 2)
				get.addHeader(header[i], header[i + 1]);
		HttpResponse res = hc.execute(get);
		try {
			Assertions.assertEquals(expectedHttpStatusCode, res.getStatusLine().getStatusCode());
		} catch (AssertionError e) {
			throw new AssertionError(e.getMessage() + " while fetching " + url);
		}
		return res;
	}

	public String postAndAssert200(String url, String body) throws IOException {
		return postAndAssert(200, url, body);
	}

	public String assertStatusCode(int expectedHttpStatusCode, HttpUriRequest request) throws IOException {
		HttpResponse res = hc.execute(request);
		Assertions.assertEquals(expectedHttpStatusCode, res.getStatusLine().getStatusCode());
		return EntityUtils.toString(res.getEntity());
	}

	public String postAndAssert(int expectedHttpStatusCode, String url, String body) throws IOException {
		return postAndAssert(expectedHttpStatusCode, url, new String[0], body);
	}

	public String postAndAssert(int expectedHttpStatusCode, String url, String[] headers, String body) throws IOException {
		return invokeAndAssert(new HttpPost(url),expectedHttpStatusCode,url,headers,body);
	}

	public String putAndAssert(int expectedHttpStatusCode, String url, String[] headers, String body) throws IOException {
		return invokeAndAssert(new HttpPut(url),expectedHttpStatusCode,url,headers,body);
	}

	public String invokeAndAssert(HttpEntityEnclosingRequestBase requestBase, int expectedHttpStatusCode, String url, String[] headers, String body) throws IOException {
		for (int i = 0; i < headers.length; i+=2)
			requestBase.setHeader(headers[i], headers[i+1]);
		requestBase.setEntity(new StringEntity(body));
		try {
			HttpResponse res = hc.execute(requestBase);
			Assertions.assertEquals(expectedHttpStatusCode, res.getStatusLine().getStatusCode());
			return EntityUtils.toString(res.getEntity());
		} finally {
			requestBase.releaseConnection();
		}
	}

	public void disableHTTPAuthentication() {
		hc = HttpClientBuilder.create().build();
	}

	public void setupHTTPAuthentication(String host, int port, String user, String pass) throws Exception {
		if (hc != null)
			close();
		hc = getAuthenticatingHttpClient(host, port, user, pass);
	}

	private static HttpClient getAuthenticatingHttpClient(String host, int port, String user, String pass) {
		BasicCredentialsProvider bcp = new BasicCredentialsProvider();
		bcp.setCredentials(new AuthScope(host, port, AuthScope.ANY_REALM), new UsernamePasswordCredentials(user, pass));
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

	public void trustAnyHTTPSServer(int port) throws NoSuchAlgorithmException, KeyManagementException {
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

	@Override
	public void close() throws Exception {
		if (hc != null) {
			HttpClientUtils.closeQuietly(hc);
			hc = HttpClientBuilder.create().build();
		}
	}

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

	public static void replaceInFile(File file, String from, String to_) throws IOException {
		writeStringToFile(file, FileUtils.readFileToString(file, UTF_8).replace(from, to_), UTF_8);
	}
}
