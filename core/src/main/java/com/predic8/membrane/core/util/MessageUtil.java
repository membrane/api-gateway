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
import org.brotli.dec.BrotliInputStream;
import org.xml.sax.*;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.sax.*;
import java.io.*;
import java.util.zip.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Request.*;

public class MessageUtil {

	private static final SAXParserFactory saxParserFactory;

	static {
		saxParserFactory = SAXParserFactory.newInstance();
		saxParserFactory.setNamespaceAware(true);
		saxParserFactory.setValidating(false);
	}

	public static InputStream getContentAsStream(Message res) throws IOException {
		if (res.isGzip()) {
			return new GZIPInputStream(res.getBodyAsStream());
		} else if (res.isDeflate()) {
			return new ByteArrayInputStream(ByteUtil.getDecompressedData(res.getBody().getContent()));
		} else if (res.isBrotli()) {
			return new BrotliInputStream(res.getBodyAsStream());
		}
		return res.getBodyAsStream();
	}
	
	public static byte[] getContent(Message res) throws Exception {
		byte[] lReturn;

		if (res.isGzip()) {
			try (InputStream lInputStream = res.getBodyAsStream();
				 GZIPInputStream lGZIPInputStream = new GZIPInputStream(lInputStream)) {
				lReturn = ByteUtil.getByteArrayData(lGZIPInputStream);
			}
		}
		else if (res.isDeflate()) {
			lReturn = ByteUtil.getDecompressedData(res.getBody().getContent());
		}
		else if (res.isBrotli()) {
			try (InputStream lInputStream = res.getBodyAsStream();
				 BrotliInputStream lBrotliInputStream = new BrotliInputStream(lInputStream)) {
				lReturn = lBrotliInputStream.readAllBytes();
			}
		}
		else {
			lReturn = res.getBody().getContent();
		}

		return lReturn;
	}
	
	public static Source getSOAPBody(InputStream stream) {
		try {
            return new SAXSource(new SOAPXMLFilter(saxParserFactory.newSAXParser().getXMLReader()), new InputSource(stream));
		} catch (ParserConfigurationException | SAXException e) {
			throw new RuntimeException("Error initializing SAXSource", e);
		}
	}

	public static Request getGetRequest(String uri) {
		Request req = getStandartRequest(METHOD_GET);
		req.setUri(uri);
		return req;
	}

	public static Request getPostRequest(String uri) {
		Request req = getStandartRequest(METHOD_POST);
		req.setUri(uri);
		return req;
	}

	public static Request getDeleteRequest(String uri) {
		Request req = getStandartRequest(METHOD_DELETE);
		req.setUri(uri);
		return req;
	}

	private static Request getStandartRequest(String method) {
		Request request = new Request();
		request.setMethod(method);
		request.setVersion(HTTP_VERSION_11);

		return request;
	}

}
