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

import org.junit.Assert;

import org.junit.Test;

import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.util.ContentTypeDetector.ContentType;

public class ContentTypeDetectorTest {
	
	@Test
	public void testJSON() {
		Assert.assertEquals(ContentType.JSON, ContentTypeDetector.detect(Response.ok().contentType(MimeType.APPLICATION_JSON_UTF8).build()).getEffectiveContentType());
	}

	@Test
	public void testXML() {
		Assert.assertEquals(ContentType.XML, ContentTypeDetector.detect(Response.ok().
				contentType(MimeType.TEXT_XML_UTF8).
				body("<foo/>").
				build()).getEffectiveContentType());
	}

	@Test
	public void testUNKNOWN() {
		Assert.assertEquals(ContentType.UNKNOWN, ContentTypeDetector.detect(Response.ok().build()).getEffectiveContentType());
	}

}
