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

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.interceptor.schemavalidation.SOAPXMLFilter;
import com.predic8.membrane.core.util.xml.parser.HardenedSaxParser;
import org.brotli.dec.BrotliInputStream;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import static com.predic8.membrane.core.util.ByteUtil.getDecompressedData;

public class MessageUtil {

	public static InputStream getContentAsStream(Message msg) {
		try {
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
		} catch (IOException e) {
			throw new ReadingBodyException(e);
		}
	}
	
	public static byte[] getContent(Message msg) {
		try {
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
		} catch (IOException e) {
			throw new ReadingBodyException(e);
		}
	}
	
	public static Source getSOAPBody(InputStream stream) {
		try {
            return new SAXSource(new SOAPXMLFilter(HardenedSaxParser.getInstance().newSAXParser().getXMLReader()), new InputSource(stream));
		} catch (SAXException e) {
			throw new RuntimeException("Error initializing SAXSource", e);
		}
	}
}