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
package com.predic8.membrane.core.interceptor.xmlcontentfilter;

import java.io.IOException;

import javax.xml.xpath.XPathExpressionException;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.util.xml.parser.XmlParseException;

import static com.predic8.membrane.core.http.MimeType.*;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.xmlunit.matchers.CompareMatcher.isSimilarTo;

public class XMLContentFilterTest {

	private final static String DOC = "<a><b c=\"12\" d=\"&quot;\"></b></a>";

	private Message getMessage() {
		Message m = new Request();
		m.setBody(new Body(DOC.getBytes()));
		return m;
	}

	private String applyXPath(String xpath) throws XPathExpressionException {
		Message m = getMessage();
		new XMLContentFilter(xpath).removeMatchingElements(m);
		return m.getBody().toString();
	}

	@Test
	public void test() throws XPathExpressionException, SAXException, IOException {
		assertThat("<a/>", isSimilarTo(applyXPath("//b")));
		assertThat("<a/>", isSimilarTo(applyXPath("//b[@c]")));
		assertThat("<a/>", isSimilarTo(applyXPath("//*[ local-name() = 'b' ]")));
		assertThat(DOC, isSimilarTo(applyXPath("//b[@c='2']")));
	}

	private static Message message(String contentType, byte[] body) {
		final var m = new Request();
		if (contentType != null)
			m.getHeader().setContentType(contentType);
		m.setBody(new Body(body));
		return m;
	}

	private static String filterSecret(Message m) throws XPathExpressionException {
		new XMLContentFilter("//secret").removeMatchingElements(m);
		return m.getBody().toString();
	}

	@Test
	void jsonIsLeftUnchanged() throws XPathExpressionException {
		assertEquals("{\"secret\":1}", filterSecret(message(APPLICATION_JSON, "{\"secret\":1}".getBytes(UTF_8))));
	}

	@Test
	void formDataIsLeftUnchanged() throws XPathExpressionException {
		assertEquals("secret=1", filterSecret(message(APPLICATION_X_WWW_FORM_URLENCODED, "secret=1".getBytes(UTF_8))));
	}

	@Test
	void xmlLabelledAsJsonIsFiltered() throws XPathExpressionException {
		assertThat("<a/>", isSimilarTo(filterSecret(message(APPLICATION_JSON, " \n<a><secret/></a>".getBytes(UTF_8)))));
	}

	/**
	 * Starts with a byte order mark, not with '<': a byte-wise check would let it pass unfiltered.
	 */
	@Test
	void utf16XmlLabelledAsJsonIsFiltered() throws XPathExpressionException {
		final var m = message(APPLICATION_JSON, "<a><secret/></a>".getBytes(UTF_16));
		new XMLContentFilter("//secret").removeMatchingElements(m);
		assertThat("<a/>", isSimilarTo(new String(m.getBody().getContent(), UTF_8)));
	}

	@Test
	void doctypeLabelledAsJsonIsRejected() {
		assertThrows(XmlParseException.class, () -> filterSecret(message(APPLICATION_JSON,
				"<!DOCTYPE a><a><secret/></a>".getBytes(UTF_8))));
	}

	@Test
	void jsonAsTextPlainIsRejected() {
		assertThrows(XmlParseException.class, () -> filterSecret(message(TEXT_PLAIN, "{\"secret\":1}".getBytes(UTF_8))));
	}

	@Test
	void jsonWithoutContentTypeIsRejected() {
		assertThrows(XmlParseException.class, () -> filterSecret(message(null, "{\"secret\":1}".getBytes(UTF_8))));
	}

	@Test
	void xopIsFiltered() throws XPathExpressionException {
		final var m = message("multipart/related; boundary=MIMEBoundary; type=\"application/xop+xml\"; start=\"<root>\"", """
				--MIMEBoundary\r
				Content-Type: application/xop+xml; charset=UTF-8; type="text/xml"\r
				Content-ID: <root>\r
				\r
				<a><secret/></a>\r
				--MIMEBoundary--\r
				""".getBytes(UTF_8));
		assertThat("<a/>", isSimilarTo(filterSecret(m)));
		assertEquals(TEXT_XML, m.getHeader().getContentType());
	}

	public void assertFastCheck(String xpath) {
		// if createElementFinder is not null, a (fast) StAX parser can be used
		// to run a first check whether the XPath expression has any chance of succeeding
		assertNotNull(XMLContentFilter.createElementFinder(xpath));
	}

	@Test
	public void testPerformance() {
		// if createElementFinder is not null, a (fast) StAX parser can be used
		// to run a first check whether the XPath expression has any chance of succeeding
		assertFastCheck("//*[local-name()='Body']");
		assertFastCheck("//*[local-name()='Body' and namespace-uri()='http://schemas.xmlsoap.org/soap/envelope/']");
		assertFastCheck("//b[@c]");
		assertFastCheck("// * [ local-name() = 'b']");
	}

}
