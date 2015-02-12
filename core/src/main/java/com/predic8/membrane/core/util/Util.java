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

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.HashMap;

import javax.mail.internet.ParseException;
import javax.net.ssl.SSLSocket;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;

import com.predic8.membrane.core.http.Response;

public class Util {
	
	public static void shutdownOutput(Socket socket) throws IOException {
		if (!(socket instanceof SSLSocket) &&
				!socket.isClosed() &&
				!socket.isOutputShutdown()) {
			socket.shutdownOutput();
		}
	}

	public static void shutdownInput(Socket socket) throws IOException {
		//SSLSocket does not implement shutdown input and output
		if (!(socket instanceof SSLSocket) && 
				!socket.isClosed() && 
				!socket.isInputShutdown()) {
			socket.shutdownInput();
		}
	}

	public static HashMap<String, String> parseSimpleJSONResponse(Response g) throws IOException, JsonParseException, ParseException {
		HashMap<String, String> values = new HashMap<String, String>();

		
		String contentType = g.getHeader().getFirstValue("Content-Type");
		if (contentType != null && g.getHeader().getContentTypeObject().match("application/json")) {
			final JsonFactory jsonFactory = new JsonFactory();
			final JsonParser jp = jsonFactory.createJsonParser(new InputStreamReader(g.getBodyAsStreamDecoded()));
			String name = null;
			while (jp.nextToken() != null) {
				switch (jp.getCurrentToken()) {
				case FIELD_NAME:
					name = jp.getCurrentName();
					break;
				case VALUE_STRING:
					values.put(name, jp.getText());
					break;
				case VALUE_NUMBER_INT:
					values.put(name, "" + jp.getLongValue());
				default:
					break;
				}
			}
		}
		return values;
	}


}
