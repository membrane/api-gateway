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

package com.predic8.membrane.servlet.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.test.AssertUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ForwardingTest {

	public static List<Object[]> getPorts() {
		return Arrays.asList(new Object[][] {
				{ 3021 }, // jetty port embedding membrane
				{ 3026 }, // membrane port
		});
	}

	@ParameterizedTest
	@MethodSource("getPorts")
	public void testReachable(int port) throws ClientProtocolException, IOException {
		String secret = "secret452363763";
		HttpClient hc = HttpClientBuilder.create().build();
		HttpPost post = new HttpPost(getBaseURL(port));
		post.setEntity(new StringEntity(secret));
		HttpResponse res = hc.execute(post);
		assertEquals(200, res.getStatusLine().getStatusCode());

		AssertUtils.assertContains(secret, EntityUtils.toString(res.getEntity()));
	}

	private void testQueryParam(int port, String param) throws ClientProtocolException, IOException {
		HttpClient hc = HttpClientBuilder.create().build();
		HttpGet get = new HttpGet(getBaseURL(port) + "?" + param);
		HttpResponse res = hc.execute(get);
		assertEquals(200, res.getStatusLine().getStatusCode());

		AssertUtils.assertContains("?" + param, EntityUtils.toString(res.getEntity()));
	}

	@ParameterizedTest
	@MethodSource("getPorts")
	public void testParam1(int port) throws ClientProtocolException, IOException {
		testQueryParam(port, "a");
	}

	@ParameterizedTest
	@MethodSource("getPorts")
	public void testParam2(int port) throws ClientProtocolException, IOException {
		testQueryParam(port, "a=1");
	}

	@ParameterizedTest
	@MethodSource("getPorts")
	public void testParam3(int port) throws ClientProtocolException, IOException {
		testQueryParam(port, "a=1&b=2");
	}

	private String getBaseURL(int port) {
		return "http://localhost:" + port + "/";
	}

}
