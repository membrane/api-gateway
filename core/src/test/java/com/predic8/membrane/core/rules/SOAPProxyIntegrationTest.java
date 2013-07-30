package com.predic8.membrane.core.rules;

import static org.junit.Assert.assertEquals;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.http.params.HttpProtocolParams;
import org.junit.Test;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;

public class SOAPProxyIntegrationTest {

	@Test
	public void test() throws Exception {
		Router router = Router.init("classpath:/soap-proxy.xml");

		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		int status = client.executeMethod(getGetMethod());
		
		assertEquals(status, 200);

		router.shutdown();
	}
	
	
	private GetMethod getGetMethod() {
		GetMethod get = new GetMethod("http://localhost:2000/axis2/services/BLZService?wsdl");
		get.setRequestHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
		get.setRequestHeader(Header.SOAP_ACTION, "");		
		return get;
	}

}
