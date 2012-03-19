package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.assertStatusCode;
import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;

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
		assertStatusCode(302, post);
	}

}
