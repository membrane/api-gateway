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

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.test.WSDLUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WSDLPublisherTest {

	public static List<Object[]> getPorts() {
		return Arrays.asList(new Object[][] {
				{ "src/test/resources/validation/ArticleService.xml", 3024 },
				{ "classpath:/validation/ArticleService.xml", 3025 },
		});
	}

	private HttpRouter router;

	public void before(String wsdlLocation, int port) throws Exception {
		router = new HttpRouter();
		ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", port), "", -1);
		WSDLPublisherInterceptor wi = new WSDLPublisherInterceptor();
		wi.setWsdl(wsdlLocation);
		wi.init(router);
		sp2.getInterceptors().add(wi);
		router.getRuleManager().addProxyAndOpenPortIfNew(sp2);
		router.init();
	}

	public void after() throws IOException {
		router.shutdown();
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("getPorts")
	public void doit(String wsdlLocation, int port) throws Exception {
		before(wsdlLocation, port);
		// this recursively fetches 5 documents (1 WSDL + 4 XSD)
		assertEquals(5, WSDLUtil.countWSDLandXSDs("http://localhost:" + port + "/articles/?wsdl"));
		after();
	}


}
