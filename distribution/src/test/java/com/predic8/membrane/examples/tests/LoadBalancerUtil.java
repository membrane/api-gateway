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

package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.test.AssertUtils.assertContains;
import static com.predic8.membrane.test.AssertUtils.assertStatusCode;
import static com.predic8.membrane.test.AssertUtils.getAndAssert200;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Assert;

public class LoadBalancerUtil {
	private static Pattern nodePattern = Pattern.compile("Mock Node (\\d+)");

	public static int getRespondingNode(String url) throws ParseException, IOException {
		Matcher m = nodePattern.matcher(getAndAssert200(url));
		Assert.assertTrue(m.find());
		return Integer.parseInt(m.group(1));

	}

	public static void addLBNodeViaHTML(String adminBaseURL, String nodeHost, int nodePort) throws ClientProtocolException, IOException {
		HttpPost post = new HttpPost(adminBaseURL + "node/save");
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("balancer", "Default"));
		params.add(new BasicNameValuePair("cluster", "Default"));
		params.add(new BasicNameValuePair("host", nodeHost));
		params.add(new BasicNameValuePair("port", "" + nodePort));
		post.setEntity(new UrlEncodedFormEntity(params));
		try {
			assertStatusCode(302, post);
		} finally {
			post.releaseConnection();
		}
	}

	public static void assertNodeStatus(String adminPageHTML, String nodeHost, int nodePort,
			String expectedNodeStatus) {
		for (String row : adminPageHTML.split("<tr>")) {
			if (row.contains(nodeHost + ":" + nodePort)) {
				assertContains(expectedNodeStatus, row);
				return;
			}
		}
		throw new AssertionError("Node " + nodeHost + ":" + nodePort + " not found in " + adminPageHTML);
	}


}
