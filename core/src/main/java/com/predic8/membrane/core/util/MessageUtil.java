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
package com.predic8.membrane.core.util;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.schemavalidation.*;
import org.brotli.dec.*;
import org.xml.sax.*;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.sax.*;
import java.io.*;
import java.util.zip.*;

import static com.predic8.membrane.core.util.ByteUtil.getDecompressedData;

public class MessageUtil {

	private static final SAXParserFactory saxParserFactory;

	static {
		saxParserFactory = SAXParserFactory.newInstance();
		saxParserFactory.setNamespaceAware(true);
		saxParserFactory.setValidating(false);
	}

	public static InputStream getContentAsStream(Message msg) throws IOException {
		if (msg.isGzip()) {
			return new GZIPInputStream(msg.getBodyAsStream());
		}
		if (msg.isDeflate()) {
			return new ByteArrayInputStream(getDecompressedData(msg.getBody().getContent()));
		}
		if (msg.isBrotli()) {
			return new BrotliInputStream(msg.getBodyAsStream());
		}
		return msg.getBodyAsStream();
	}
	
	public static byte[] getContent(Message msg) throws Exception {
		if (msg.isGzip()) {
			try (InputStream lInputStream = msg.getBodyAsStream();
				 GZIPInputStream lGZIPInputStream = new GZIPInputStream(lInputStream)) {
                return lGZIPInputStream.readAllBytes();
            }
		}
		if (msg.isDeflate()) {
			return getDecompressedData(msg.getBody().getContent());
		}
		if (msg.isBrotli()) {
			try (InputStream lInputStream = msg.getBodyAsStream();
				 BrotliInputStream lBrotliInputStream = new BrotliInputStream(lInputStream)) {
				return lBrotliInputStream.readAllBytes();
			}
		}
		return msg.getBody().getContent();
	}
	
	public static Source getSOAPBody(InputStream stream) {
		try {
            return new SAXSource(new SOAPXMLFilter(saxParserFactory.newSAXParser().getXMLReader()), new InputSource(stream));
		} catch (ParserConfigurationException | SAXException e) {
			throw new RuntimeException("Error initializing SAXSource", e);
		}
	}
}