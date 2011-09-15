package com.predic8.membrane.core.transport.http;


import static org.junit.Assert.assertTrue;

import java.net.InetAddress;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.http.entity.StringEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.rules.Rule;

public class HTTPContinueTest {

	
	@Test
	public void testContinue() throws Exception {
//		Rule rule2000 = new ServiceProxy(new ForwardingRuleKey("localhost", "*", ".*", 2000), "predic8.com", 80);
//		Router router = new HttpRouter();
//		router.getRuleManager().addRuleIfNew(rule2000);

		HttpClient client = new HttpClient();
		
		PostMethod post = new PostMethod("http://localhost:2000");
		
		post.setRequestEntity(new StringRequestEntity("Hallo World!","text/plain","UTF-8"));
		
		post.getParams().setBooleanParameter(HttpMethodParams.USE_EXPECT_CONTINUE, true);
		
		client.executeMethod(post);
	}
}
