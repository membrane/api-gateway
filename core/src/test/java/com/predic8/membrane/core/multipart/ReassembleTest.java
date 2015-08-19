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

import java.io.IOException;

import javax.mail.internet.ParseException;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;
import javax.xml.xpath.XPathExpressionException;

import org.junit.Assert;

import org.apache.commons.fileupload.MultipartStream.MalformedStreamException;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.custommonkey.xmlunit.XMLAssert;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.XMLContentFilter;
import com.predic8.membrane.core.util.ContentTypeDetector;
import com.predic8.membrane.core.util.ContentTypeDetector.ContentType;
import com.predic8.membrane.core.util.EndOfStreamException;

public class ReassembleTest {

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

	@Test
	public void doit() throws HttpException, IOException, SAXException, XMLStreamException {
		String actual = IOUtils.toString(new XOPReconstitutor().reconstituteIfNecessary(getResponse()));
		String expected = IOUtils.toString(getClass().getResourceAsStream("/multipart/embedded-byte-array-reassembled.xml"));

		if (actual.startsWith("--"))
			throw new AssertionError("Response was not reassembled: " + actual);

		XMLAssert.assertXMLEqual(expected, actual);
	}

	@Test
	public void checkContentType() throws ParseException, MalformedStreamException, IOException, EndOfStreamException, XMLStreamException, FactoryConfigurationError {
		Assert.assertEquals("text/xml",
				new XOPReconstitutor().getReconstitutedMessage(getResponse()).getHeader().getContentType());
	}

	private void testXMLContentFilter(String xpath, int expectedNumberOfRemainingElements) throws IOException, XPathExpressionException {
		XMLContentFilter cf = new XMLContentFilter(xpath);
		Message m = getResponse();
		cf.removeMatchingElements(m);
		Assert.assertEquals("text/xml", m.getHeader().getContentType());
		Assert.assertEquals(expectedNumberOfRemainingElements+1, StringUtils.countMatches(m.getBody().toString(), "<"));
	}

	@Test
	public void inCombinationWithXMLContentFilterTest() throws XPathExpressionException, IOException {
		testXMLContentFilter("//*[local-name()='Body']", 1);
		testXMLContentFilter("//*[local-name()='Body' and namespace-uri()='http://schemas.xmlsoap.org/soap/envelope/']", 1);
	}

	@Test
	public void testContentTypeDetector() throws IOException {
		Assert.assertEquals(ContentType.SOAP, ContentTypeDetector.detect(getResponse()).getEffectiveContentType());
	}

}
