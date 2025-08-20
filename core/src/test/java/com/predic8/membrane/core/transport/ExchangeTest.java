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

import com.predic8.membrane.core.exchange.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ExchangeTest {

	@Test
	void testStringProperties() {
		Exchange exc = new Exchange(null);

		exc.setProperty("Integer", 906090);
		exc.setProperty("Hallo", "Hallo");
		exc.setProperty("Title", "Meteor");

		Map<String, String> props = exc.getStringProperties();

		assertTrue(props.containsKey("Hallo"));
		assertTrue(props.containsKey("Title"));
		assertFalse(props.containsKey("Integer"));
	}

	@Test
	void hostHeaderWithPort() {
		Exchange exc = new Exchange(null);
		exc.setOriginalHostHeader("localhost:2000");
		assertEquals("localhost",exc.getOriginalHostHeaderHost());
		assertEquals("2000",exc.getOriginalHostHeaderPort());
	}

	@Test
	void hostHeaderWithoutPort() {
		Exchange exc = new Exchange(null);
		exc.setOriginalHostHeader("localhost");
		assertEquals("localhost",exc.getOriginalHostHeaderHost());
		assertEquals("",exc.getOriginalHostHeaderPort());
	}

	@Test
	void getPropertyOrNull() {
		Exchange exc = new Exchange(null);
		exc.setProperty("foo", "10");
		assertEquals("10",exc.getPropertyOrNull("foo", String.class));
        assertNull(exc.getPropertyOrNull("foo", Integer.class));
	}
}
