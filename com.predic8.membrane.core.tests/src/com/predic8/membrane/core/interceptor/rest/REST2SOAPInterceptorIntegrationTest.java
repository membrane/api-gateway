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
public class REST2SOAPInterceptorIntegrationTest {

	private static HttpRouter router;

	
	@Before
	public void setUp() throws Exception {
		Rule rule = new ForwardingRule(new ForwardingRuleKey("localhost", "*", ".*", 8000), "thomas-bayer.com", 80);
		router = new HttpRouter();
		router.getRuleManager().addRuleIfNew(rule);
		
		
		REST2SOAPInterceptor rest2SoapInt = new REST2SOAPInterceptor();
		Map<String, Map<String, String> > mappings = new HashMap<String, Map<String, String>>();
		mappings.put("/bank/.*", getBLZMapping());
		rest2SoapInt.setMappings(mappings);
		router.getTransport().getInterceptors().add(rest2SoapInt);		
	}

	private Map<String, String> getBLZMapping() {
		Map<String,String> mapping = new HashMap<String, String>();
		mapping.put("SOAPAction", "");
		mapping.put("SOAPURL", "/axis2/services/BLZService");
		mapping.put("requestXSLT", "classpath:/blz-httpget2soap-request.xsl");
		mapping.put("responseXSLT", "classpath:/strip-soap-envelope.xsl");
		return mapping;
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
