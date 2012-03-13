package com.predic8.membrane.core.multipart;

import java.io.IOException;

import org.apache.commons.httpclient.HttpException;
import org.apache.commons.io.IOUtils;
import org.custommonkey.xmlunit.XMLAssert;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.predic8.membrane.core.http.Response;

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
	public void doit() throws HttpException, IOException, SAXException {
		String actual = IOUtils.toString(new SOAPMessageAccessor().getSOAPStream(getResponse()));
		String expected = IOUtils.toString(getClass().getResourceAsStream("/multipart/embedded-byte-array-reassembled.xml"));
		
		if (actual.startsWith("--"))
			throw new AssertionError("Response was not reassembled: " + actual);
		
		XMLAssert.assertXMLEqual(expected, actual);
	}

}
