package com.predic8.membrane.core.interceptor.authentication;

import static junit.framework.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.http.params.HttpProtocolParams;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;
public class BasicAuthenticationInterceptorIntegrationTest {

	private static HttpRouter router;

	@Test
	public void testDeny() throws Exception {
		Rule rule = new ForwardingRule(new ForwardingRuleKey("localhost", "*", ".*", 8000), "thomas-bayer.com", 80);
		HttpRouter router = new HttpRouter();
		router.getRuleManager().addRuleIfNew(rule);
		
		BasicAuthenticationInterceptor interceptor = new BasicAuthenticationInterceptor();
		Map<String, String> mapping = new HashMap<String, String>();
		mapping.put("admin", "admin");
		interceptor.setUsers(mapping );
		
		router.getTransport().getInterceptors().add(interceptor);

		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		int status = client.executeMethod(getGetMethod());
	
	    assertEquals(401, status);	   
	    //TODO Basic Authentication test
	}
	
	private GetMethod getGetMethod() {
		GetMethod get = new GetMethod("http://localhost:8000/axis2/services/BLZService?wsdl");
		get.setRequestHeader(Header.CONTENT_TYPE, "text/xml;charset=UTF-8");
		get.setRequestHeader(Header.SOAP_ACTION, "");		
		return get;
	}

	
}
