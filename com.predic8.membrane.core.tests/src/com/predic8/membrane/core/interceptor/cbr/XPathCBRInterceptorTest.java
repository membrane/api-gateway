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

import java.io.InputStream;
import java.util.*;

import junit.framework.*;

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
		DefaultRouteProvider rp = new DefaultRouteProvider();
		rp.setRoutes(getRouteList("//CITY[text()='England']","http://www.host.uk/service",
								  "//CITY[text()='Bonn']","http://www.host.de/service"));
		i.setRouteProvider(rp);
		
		i.handleRequest(exc);
		Assert.assertEquals("http://www.host.de/service", exc.getDestinations().get(0));
		
	}

	@Test
	public void testRoutingNSAware() throws Exception {
		exc = new Exchange();
		Request res = new Request();		
		res.setBodyContent(getByteArrayData(getClass().getResourceAsStream("/customerFromBonnWithNS.xml")));
		exc.setRequest(res);

		XPathCBRInterceptor i = new XPathCBRInterceptor();
		
		DefaultRouteProvider rp = new DefaultRouteProvider();
		rp.setRoutes(getRouteList("//pre:CITY[text()='England']","http://www.host.uk/service",
								  "//pre:CITY[text()='Bonn']","http://www.host.de/service"));
		
		i.setRoutProvider(rp);		
		i.setNamespaces(getNamespaceMap("pre", "http://predic8.de/customer/1"));
		
		i.handleRequest(exc);
		Assert.assertEquals("http://www.host.de/service", exc.getDestinations().get(0));
		
	}

	private List<Route> getRouteList(String... args) {
		List<Route> l = new ArrayList<Route>();
		for (int i = 0; i < args.length; i+=2) {
			l.add(new Route(args[i],args[i+1]));
		}
		return l;
	}

	private Map<String, String> getNamespaceMap(String... args) {
		Map<String, String> map = new HashMap<String, String>();
		for (int i = 0; i < args.length; i+=2) {
			map.put(args[i], args[i+1]);
		}
		return map;
	}
	
	private void printBodyContent() throws Exception {
		InputStream i = exc.getRequest().getBodyAsStream();
		int read = 0;
		byte[] buf = new byte[4096];
		while ((read = i.read(buf)) != -1) {
			System.out.write(buf, 0, read);
		}
	}
	
}
