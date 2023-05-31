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

package com.predic8.membrane.core.multipart;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.util.ContentTypeDetector.ContentType;
import jakarta.mail.internet.*;
import org.apache.commons.io.*;
import org.apache.commons.lang3.*;
import org.junit.jupiter.api.*;

import javax.xml.stream.*;
import javax.xml.xpath.*;
import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static java.nio.charset.StandardCharsets.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.xmlunit.matchers.CompareMatcher.*;

public class ReassembleTest {

	@SuppressWarnings("DataFlowIssue")
	private Response getResponse() throws IOException {
		byte[] body = IOUtils.toByteArray(getClass().getResourceAsStream("/multipart/embedded-byte-array.txt"));
		return Response.ok().
				header("Content-Type", "multipart/related; " +
						"type=\"application/xop+xml\"; " +
						"boundary=\"uuid:168683dc-43b3-4e71-8e66-efb633ef406b\"; " +
						"start=\"<root.message@cxf.apache.org>\"; " +
						"start-info=\"text/xml\"").
						header("Content-Length", ""+body.length).
						body(body).
						build();
	}

	@SuppressWarnings("DataFlowIssue")
	@Test
	public void doit() throws IOException {
		String actual = IOUtils.toString(new XOPReconstitutor().reconstituteIfNecessary(getResponse()), UTF_8);
		String expected = IOUtils.toString(getClass().getResourceAsStream("/multipart/embedded-byte-array-reassembled.xml"), UTF_8);

		if (actual.startsWith("--"))
			throw new AssertionError("Response was not reassembled: " + actual);

		assertThat(expected, isSimilarTo(actual));
	}

	@Test
	public void checkContentType() throws ParseException, IOException, EndOfStreamException, XMLStreamException, FactoryConfigurationError {
		assertEquals("text/xml",
				new XOPReconstitutor().getReconstitutedMessage(getResponse()).getHeader().getContentType());
	}

	private void testXMLContentFilter(String xpath, int expectedNumberOfRemainingElements) throws IOException, XPathExpressionException {
		XMLContentFilter cf = new XMLContentFilter(xpath);
		Message m = getResponse();
		cf.removeMatchingElements(m);

		assertEquals(TEXT_XML, m.getHeader().getContentType());
		assertEquals(expectedNumberOfRemainingElements+1, StringUtils.countMatches(m.getBody().toString(), "<"));
	}

	@Test
	public void inCombinationWithXMLContentFilterTest() throws XPathExpressionException, IOException {
		testXMLContentFilter("//*[local-name()='Body']", 1);
		testXMLContentFilter("//*[local-name()='Body' and namespace-uri()='http://schemas.xmlsoap.org/soap/envelope/']", 1);
	}

	@Test
	public void testContentTypeDetector() throws IOException {
		assertEquals(ContentType.SOAP, ContentTypeDetector.detect(getResponse()).getEffectiveContentType());
	}

}
