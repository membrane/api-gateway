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
package com.predic8.membrane.core.interceptor;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCInterceptor;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;

@MCInterceptor(name="limit")
public class LimitInterceptor extends AbstractInterceptor {

	private static Logger log = LogManager.getLogger(LimitInterceptor.class);

	private long maxBodyLength = -1;
	
	public LimitInterceptor() {
		name = "Limit Interceptor";
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		return handleMessage(exc, exc.getRequest());
	}
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		return handleMessage(exc, exc.getResponse());
	}
	
	@Override
	public String getShortDescription() {
		return maxBodyLength == -1 ? "" : "Limit the length of message bodies to " + maxBodyLength + " bytes.";
	}
	
	@Override
	public String getHelpId() {
		return "limit";
	}
	
	public long getMaxBodyLength() {
		return maxBodyLength;
	}
	
	@MCAttribute
	public void setMaxBodyLength(long maxBodyLength) {
		this.maxBodyLength = maxBodyLength;
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		String maxBodyLengthStr = token.getAttributeValue("", "maxBodyLength");
		maxBodyLength = maxBodyLengthStr == null ? -1 : Long.parseLong(maxBodyLengthStr);
	}
	
	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {
		out.writeStartElement("limit");
		if (maxBodyLength != -1)
			out.writeAttribute("maxBodyLength", ""+maxBodyLength);		
		out.writeEndElement();
	}

	private Outcome handleMessage(Exchange exc, Message msg) throws IOException {
		if (maxBodyLength == -1)
			return Outcome.CONTINUE;
		
		int len = msg.getHeader().getContentLength();
		if (len != -1 && len > maxBodyLength) {
			log.info("Message length (" + len + ") exceeded limit (" + maxBodyLength + ")");
			exc.setResponse(createFailureResponse());
			return Outcome.ABORT;
		}
		
		msg.setBody(new Body(new LengthLimitingStream(msg.getBodyAsStream())));
		
		return Outcome.CONTINUE;
	}
	
	private Response createFailureResponse() {
		return Response.badRequest("Message bodies must be smaller than " + maxBodyLength + " bytes.").build();
	}
	
	public class LengthLimitingStream extends InputStream {
		
		private final InputStream is;

		private long pos;
		
		public LengthLimitingStream(InputStream is) {
			this.is = is;
		}

		private void checkPosition() throws IOException {
			if (pos > maxBodyLength) {
				log.info("Message length (>=" + pos + ") exceeded limit (" + maxBodyLength + ")");
				throw new IOException("Message body too large.");
			}
		}
		
		@Override
		public int read() throws IOException {
			int i = is.read();
			if (i == -1)
				return i;
			pos++;
			checkPosition();
			return i;
		}

		public int read(byte[] b) throws IOException {
			int l = is.read(b);
			if (l == -1)
				return l;
			pos += l;
			checkPosition();
			return l;
		}

		public int read(byte[] b, int off, int len) throws IOException {
			int l = is.read(b, off, len);
			if (l == -1)
				return l;
			pos += l;
			checkPosition();
			return l;
		}

		public long skip(long n) throws IOException {
			long l = is.skip(n);
			if (l == -1)
				return l;
			pos += l;
			checkPosition();
			return l;
		}

		public int available() throws IOException {
			return is.available();
		}

		public String toString() {
			return "LengthLimitingStream(" + is.toString() + ")";
		}

		public void close() throws IOException {
			is.close();
		}

		public boolean markSupported() {
			return false;
		}
		
	}

}
