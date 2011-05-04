package com.predic8.membrane.core.interceptor.rest;

import static junit.framework.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.http.params.HttpProtocolParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.interceptor.rewrite.RegExURLRewriteInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.SimpleURLRewriteInterceptor;
import com.predic8.membrane.core.interceptor.xslt.*;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;
public class RESTBLZServiceIntegrationTest {

	private static HttpRouter router;

	
	@Before
	public void setUp() throws Exception {
		Rule rule = new ForwardingRule(new ForwardingRuleKey("localhost", "*", ".*", 8000), "thomas-bayer.com", 80);
		router = new HttpRouter();
		router.getRuleManager().addRuleIfNew(rule);
		
		
		HTTP2XMLInterceptor http2xml = new HTTP2XMLInterceptor();
		router.getTransport().getInterceptors().add(http2xml);

		RegExURLRewriteInterceptor urlRewriter = new RegExURLRewriteInterceptor();
		Map<String, String> mapping = new HashMap<String, String>();
		mapping.put("/bank/.*", "/axis2/services/BLZService");
		urlRewriter.setMapping(mapping );
		router.getTransport().getInterceptors().add(urlRewriter);
		
		XSLTInterceptor xslt = new XSLTInterceptor();
		xslt.setRequestXSLT("classpath:/blz-httpget2soap-request.xsl");
		xslt.setResponseXSLT("classpath:/strip-soap-envelope.xsl");
		router.getTransport().getInterceptors().add(xslt);
		
	}

	@After
	public void tearDown() throws Exception {
		router.getTransport().closeAll();
	}

	@Test
	public void testRest() throws Exception {
		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		GetMethod get = new GetMethod("http://localhost:8000/bank/37050198");
		
		int status = client.executeMethod(get);
		System.out.println(get.getResponseBodyAsString());
		
		assertEquals(200, status);			    
	}	
}
