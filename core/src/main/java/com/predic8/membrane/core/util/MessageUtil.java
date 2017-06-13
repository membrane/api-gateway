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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;

import org.xml.sax.InputSource;
import org.xml.sax.helpers.XMLReaderFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;

public class MessageUtil {

	public static InputStream getContentAsStream(Message res) throws IOException {
		if (res.isGzip()) {
			return new GZIPInputStream(res.getBodyAsStream());
		} else if (res.isDeflate()) {
			return new ByteArrayInputStream(ByteUtil.getDecompressedData(res.getBody().getContent()));
		}
		return res.getBodyAsStream();
	}

	public static byte[] getContent(Message res) throws Exception {
		if (res.isGzip()) {
			return ByteUtil.getByteArrayData(new GZIPInputStream(res.getBodyAsStream()));
		} else if (res.isDeflate()) {
			return ByteUtil.getDecompressedData(res.getBody().getContent());
		}
		return res.getBody().getContent();
	}

	public static Request getGetRequest(String uri) {
		Request req = getStandartRequest(Request.METHOD_GET);
		req.setUri(uri);
		return req;
	}

	public static Request getPostRequest(String uri) {
		Request req = getStandartRequest(Request.METHOD_POST);
		req.setUri(uri);
		return req;
	}

	public static Request getDeleteRequest(String uri) {
		Request req = getStandartRequest(Request.METHOD_DELETE);
		req.setUri(uri);
		return req;
	}

	private static Request getStandartRequest(String method) {
		Request request = new Request();
		request.setMethod(method);
		request.setVersion(Constants.HTTP_VERSION_11);

		return request;
	}

}
