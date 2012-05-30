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
package com.predic8.membrane.core.interceptor.server;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import junit.framework.Assert;

import org.apache.http.ParseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.test.WSDLUtil;

@RunWith(Parameterized.class)
public class WSDLPublisherTest {
		
	@Parameters
	public static List<Object[]> getPorts() {
		return Arrays.asList(new Object[][] { 
				{ "src\\test\\resources\\validation\\ArticleService.xml", 3024 },
				{ "classpath:/validation/ArticleService.xml", 3025 },
		});
	}

	private String wsdlLocation;
	private int port;
	private HttpRouter router;

	public WSDLPublisherTest(String wsdlLocation, Integer port) {
		this.wsdlLocation = wsdlLocation;
		this.port = port;
	}
	
	@Before
	public void before() throws Exception {
		router = new HttpRouter();
		ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", port), "", -1);
		WSDLPublisherInterceptor wi = new WSDLPublisherInterceptor();
		wi.setWsdl(wsdlLocation);
		wi.setRouter(router);
		wi.doAfterParsing();
		sp2.getInterceptors().add(wi);
		router.getRuleManager().addProxyIfNew(sp2);
	}
	
	@After
	public void after() throws IOException {
		router.getTransport().closeAll();
	}
	
	@Test
	public void doit() throws ParseException, IOException, XMLStreamException {
		// this recursively fetches 4 documents (1 WSDL + 3 XSD)
		Assert.assertEquals(4, WSDLUtil.countWSDLandXSDs("http://localhost:" + port + "/articles/?wsdl"));
	}
	
	
}
