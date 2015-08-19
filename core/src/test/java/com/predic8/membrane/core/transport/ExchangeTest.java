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

package com.predic8.membrane.core.transport;

import java.util.Map;

import org.junit.Assert;

import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;

public class ExchangeTest {

	@Test
	public void testStringProperties() throws Exception {
		Exchange exc = new Exchange(null);

		exc.setProperty("Integer", new Integer(906090));
		exc.setProperty("Hallo", "Hallo");
		exc.setProperty("Title", "Meteor");

		Map<String, String> props = exc.getStringProperties();

		Assert.assertTrue(props.containsKey("Hallo"));
		Assert.assertTrue(props.containsKey("Title"));
		Assert.assertFalse(props.containsKey("Integer"));
	}

}
