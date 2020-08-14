/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import junit.framework.TestCase;

import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;

public class XPathCBRInterceptorTest extends TestCase {

	Exchange exc = new Exchange(null);

	@Test
	public void testRouting() throws Exception {
		exc = new Exchange(null);
		Request res = new Request();
		res.setBodyContent(getByteArrayData(getClass().getResourceAsStream("/customerFromBonn.xml")));
		exc.setRequest(res);

		XPathCBRInterceptor i = new XPathCBRInterceptor();
		i.setCases(getRouteList("//CITY[text()='England']","http://www.host.uk/service",
				"//CITY[text()='Bonn']","http://www.host.de/service"));

		i.handleRequest(exc);
		Assert.assertEquals("http://www.host.de/service", exc.getDestinations().get(0));

	}

	@Test
	public void testRoutingNSAware() throws Exception {
		exc = new Exchange(null);
		Request res = new Request();
		res.setBodyContent(getByteArrayData(getClass().getResourceAsStream("/customerFromBonnWithNS.xml")));
		exc.setRequest(res);

		XPathCBRInterceptor i = new XPathCBRInterceptor();

		i.setCases(getRouteList("//pre:CITY[text()='England']","http://www.host.uk/service",
				"//pre:CITY[text()='Bonn']","http://www.host.de/service"));

		i.setNamespaces(getNamespaceMap("pre", "http://predic8.de/customer/1"));

		i.handleRequest(exc);
		Assert.assertEquals("http://www.host.de/service", exc.getDestinations().get(0));

	}

	private List<Case> getRouteList(String... args) {
		List<Case> l = new ArrayList<Case>();
		for (int i = 0; i < args.length; i+=2) {
			l.add(new Case(args[i],args[i+1]));
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
}
