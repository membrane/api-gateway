/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.cbr;

import static com.predic8.membrane.core.util.ByteUtil.getByteArrayData;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;

public class XPathCBRInterceptorTest extends TestCase {
		
	Exchange exc = new Exchange();
	
	@Test
	public void testRouting() throws Exception {
		exc = new Exchange();
		Request res = new Request();		
		res.setBodyContent(getByteArrayData(getClass().getResourceAsStream("/customerFromBonn.xml")));
		exc.setRequest(res);

		XPathCBRInterceptor i = new XPathCBRInterceptor();
		List<Route> routes = new ArrayList<Route>();
		routes.add(new Route("//CITY[text()='England']","http://www.host.uk/service"));
		routes.add(new Route("//CITY[text()='Bonn']","http://www.host.de/service"));
		i.setRoutes(routes);
		
		i.handleRequest(exc);
		Assert.assertEquals("http://www.host.de/service", exc.getDestinations().get(0));
		
	}
	
}
