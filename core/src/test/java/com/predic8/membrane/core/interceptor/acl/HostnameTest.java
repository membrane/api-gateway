/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.acl;

import com.predic8.membrane.core.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class HostnameTest {

	static Router router;

	static Hostname h1;
	static Hostname h2;

	@BeforeAll
	public static void setUp() throws Exception {
		router = new Router();

		h1 = new Hostname(router);
		h1.setSchema("^localhost$");

		h2 = new Hostname(router);
		h2.setSchema("local*");
	}

	@Test
	public void matchesPlainString() {
        assertTrue(h1.matches("localhost", "localhost"));
	}

	@Test
	public void notMatchesPlainString() {
        assertFalse(h1.matches("local", "local"));
	}

	@Test
	public void matchesRegexString() {
        assertTrue(h1.matches("localhost", "localhost"));
	}

	@Test
	public void notMatchesRegexString() {
        assertFalse(h1.matches("hostlocal", "hostlocal"));
	}

}
