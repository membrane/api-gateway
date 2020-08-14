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

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

import com.predic8.membrane.test.AssertUtils;

public class ReleaseConfigurationTest {

	private final int port;

	public ReleaseConfigurationTest() {
		this.port = 3021;
	}

	@Test
	public void testReachable() throws ClientProtocolException, IOException {
		String secret = "Web Services";
		HttpClient hc = HttpClientBuilder.create().build();
		HttpPost post = new HttpPost(getBaseURL());
		post.setEntity(new StringEntity(secret));
		HttpResponse res = hc.execute(post);
		assertEquals(200, res.getStatusLine().getStatusCode());

		AssertUtils.assertContains(secret, EntityUtils.toString(res.getEntity()));
	}

	private String getBaseURL() {
		return "http://localhost:" + port + "/release/";
	}

}
