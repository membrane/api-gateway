package com.predic8.membrane.integration;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.http.params.HttpProtocolParams;

import com.predic8.membrane.core.Configuration;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;

public class AccessControlInterceptorTest extends TestCase {

	public static final String FILE_WITH_VALID_SERVICE_PARAMS = "resources/acl-valid-service.xml";
	
	public static final String FILE_WITH_PATH_MISMATCH = "resources/acl-path-mismatch.xml";
	
	public static final String FILE_WITH_CLIENT_MISMATCH = "resources/acl-client-mismatch.xml";
	
	private static HttpRouter router;
	
	@Override
	protected void setUp() throws Exception {
		Rule rule = new ForwardingRule(new ForwardingRuleKey("localhost", "POST", ".*", 8000), "thomas-bayer.com", "80");
		router = new HttpRouter();
		router.getConfigurationManager().setConfiguration(new Configuration());
		router.getRuleManager().addRuleIfNew(rule);
		
		router.getTransport().closeAll();
		router.getTransport().openPort(8000);
	}
	
	@Override
	protected void tearDown() throws Exception {
		router.getTransport().closeAll();
	}
	
	public void testValidServiceFile() throws Exception {
		setInterceptor(FILE_WITH_VALID_SERVICE_PARAMS);
		
		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION  , HttpVersion.HTTP_1_0);
		
		PostMethod post = getPostMethod();
		assertEquals(200, client.executeMethod(post));	
	}
	
	public void testPathMismatchFile() throws Exception {
		setInterceptor(FILE_WITH_PATH_MISMATCH);
		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION  , HttpVersion.HTTP_1_0);
		
		PostMethod post = getPostMethod();
		assertEquals(403, client.executeMethod(post));
	}
	
	public void testClientsMismatchFile() throws Exception {
		setInterceptor(FILE_WITH_CLIENT_MISMATCH);
		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION  , HttpVersion.HTTP_1_0);
		
		PostMethod post = getPostMethod();
		assertEquals(403, client.executeMethod(post));
	}

	private void setInterceptor(String fileName) {
		AccessControlInterceptor interceptor = new AccessControlInterceptor();
		interceptor.setAclFilename(fileName);
		router.getTransport().getInterceptors().add(interceptor);
	}
	
	
	private PostMethod getPostMethod() {
		PostMethod post = new PostMethod("http://localhost:8000/axis2/services/BLZService");
		InputStream stream = this.getClass().getResourceAsStream("/getBank.xml");
		
		InputStreamRequestEntity entity = new InputStreamRequestEntity(stream);
		post.setRequestEntity(entity); 
		post.setRequestHeader("Content-Type", "text/xml;charset=UTF-8");
		post.setRequestHeader("SOAPAction", "\"\"");
		return post;
	}
	
}
