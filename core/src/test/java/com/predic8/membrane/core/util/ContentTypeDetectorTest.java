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
package com.predic8.membrane.core.util;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.util.ContentTypeDetector.*;
import static com.predic8.membrane.core.util.ContentTypeDetector.EffectiveContentType.*;
import static org.junit.jupiter.api.Assertions.*;

public class ContentTypeDetectorTest {

	@Test
	void json() {
		assertEquals(JSON, detectEffectiveContentType(ok().contentType(APPLICATION_JSON_UTF8).build()));
	}

	@Test
	void xml() {
		assertEquals(XML, detectEffectiveContentType(ok().
				contentType(TEXT_XML_UTF8).
				body("<foo/>").
				build()));
	}

	@Test
	void xmlContentTypeButParseErrors() {
		assertEquals(XML, detectEffectiveContentType(ok().
				contentType(TEXT_XML_UTF8).
				body("Wrong!<foo/>").
				build()));
	}

	@Test
	void unknown() {
		assertEquals(UNKNOWN, detectEffectiveContentType(ok().build()));
	}

}
