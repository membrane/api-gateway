/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.HttpRouter;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AccessControlParserTest {

	public static final String FILE_NAME = "classpath:/acl/acl.xml";

	public static final String RESOURCE_URI_1 = "/axis2/services";

	public static final String RESOURCE_URI_2 = "/crm/kundenservice";

	private static List<Resource> resources;

	@BeforeAll
	protected static void setUp() throws Exception {
		resources = new AccessControlInterceptor().parse(FILE_NAME, new HttpRouter()).getResources();
	}

	@Test
	public void testResourceCount() throws Exception {
		assertEquals(3, resources.size());
	}

}
