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

package com.predic8.membrane.core.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.HttpUtil;


public abstract class Message {

	private static Log log = LogFactory.getLog(Message.class.getName());
	
	protected Header header;
	
	protected AbstractBody body;
	
	protected String version = "1.1";

	private boolean released = false;
	
	private String errorMessage = "";
	
	
	
	public Message() {
		header = new Header();
		body = new EmptyBody();
		
	}

	/**
	 * If the message is HTTP 1.1 but the header has no information about the
	 * content length, then an assumption is made that after the body the server
	 * will send an EOF. So the body is read till end of the stream.
	 * 
	 * See http://www.ietf.org/rfc/rfc2145.txt
	 */
	public void read(InputStream in, boolean createBody) throws IOException, EndOfStreamException {
		parseStartLine(in);		
		header = new Header(in);
				
		if (createBody) 
		  createBody(in);
	} 

	public void readBody() throws IOException {
		body.read();
	}
	
	public AbstractBody getBody() {
		return body;
	}
	
	public InputStream getBodyAsStream() {
		try {
			return body.getBodyAsStream();
		} catch (IOException e) {
			e.printStackTrace();
			log.error("Could not get body as stream");
			throw new RuntimeException("Could not get body as stream");
		}
	}

	public void setBody(AbstractBody b) {
		body = b;
	}

	public void setBodyContent(byte[] content) {
		body = new Body(content);
		header.removeFields(Header.TRANSFER_ENCODING);
		header.setContentLength(content.length);
	}
	
	protected void createBody(InputStream in) throws IOException {
 		log.debug("createBody");
		if (isHTTP10()) {
			body = new Body(in, header.getContentLength()); 
			return;
		}
		
		if (header.isChunked()) {
			body = new ChunkedInOutBody(in);
			return;
		}
		
		if (!isKeepAlive()  || header.hasContentLength() || header.isProxyConnectionClose()) {			
			body = new Body(in, header.getContentLength());
			return;
		}
		
		
		if (log.isDebugEnabled()) {
			log.error("Message has no content length: " + toString());
		}
		
		// Message is HTTP 1.1 but the header has no information about the content length.
		// An assumption is made that after the body the server will send EOF. So the body is read till end of the stream
		// See http://www.ietf.org/rfc/rfc2145.txt
		body = new Body(in, -1); 
	}

	abstract protected void parseStartLine(InputStream in) throws IOException, EndOfStreamException;

	public Header getHeader() {
		return header;
	}

	/**
	 *preserve synchronized keyword, notify method is called  
	 */
	public synchronized void release() {
		notify();
		released = true;
	}

	public boolean hasMsgReleased() {
		return released;
	}

	public void setHeader(Header srcHeader) {
		header = srcHeader;
	}

	public final void write(OutputStream out) throws IOException {
		writeStartLine(out);
		header.write(out);
		out.write(Constants.CRLF_BYTES);
		
		if (header.is100ContinueExpected()) {
			out.flush();
			return;
		}
			
		body.write(getHeader().isChunked() ? new ChunkedBodyWriter(out) : new BodyWriter(out));
		
		out.flush();
	}
	
	/**
	 * The start line supposedly only contains ASCII characters. But since
	 * {@link HttpUtil#readLine(InputStream)} converts the input byte-by-byte
	 * to char-by-char, we use ISO-8859-1 for output.  
	 */
	public void writeStartLine(OutputStream out) throws IOException {
		out.write(getStartLine().getBytes(Constants.ISO_8859_1_CHARSET));
	}

	public abstract String getStartLine();
	
	public boolean isHTTP11() {
		return version.equalsIgnoreCase("1.1");
	}
	
	public boolean isHTTP10() {
		return version.equalsIgnoreCase("1.0");
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}


	@Override
	public String toString() {
	    return getStartLine() + header.toString() + Constants.CRLF + body.toString();
	}
	
	public boolean isKeepAlive() {
		if (isHTTP10())
			return false;
		if (header.getConnection() == null)
			return true;
		if (header.isConnectionClose()) 
			return false;
		
		if (header.isProxyConnectionClose())
			return false;
		
		return true;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getName() {
		return "message";
	}

	public boolean isBodyEmpty() throws IOException {
		if (header.hasContentLength()) 
			return header.getContentLength() == 0;
		
		
		if (getBody().read)
			return getBody().getLength() == 0;
		
		return false;
	}
	
	
	public boolean isImage() {
		if (header.getContentType() == null) 
			return false;
		return header.getContentType().indexOf("image") >= 0;
	}
	
	public boolean isXML() {
		if (header.getContentType() == null) 
			return false;
		return header.getContentType().indexOf("xml") > 0;
	}
	
	public boolean isJSON() {
		if (header.getContentType() == null) 
			return false;
		return header.getContentType().indexOf("json") > 0;
	}
	
	public boolean isHTML() {
		if (header.getContentType() == null) 
			return false;
		return header.getContentType().indexOf("html") > 0;
	}
	
	public boolean isCSS() {
		if (header.getContentType() == null) 
			return false;
		return header.getContentType().indexOf("css") > 0;
	}
	
	public boolean isJavaScript() {
		if (header.getContentType() == null) 
			return false;
		return header.getContentType().indexOf("javascript") > 0;
	}
	
	public boolean isGzip() {
		return "gzip".equalsIgnoreCase(header.getContentEncoding());
	}
	
	public boolean isDeflate() {
		return "deflate".equalsIgnoreCase(header.getContentEncoding());
	}
	
	public String getCharset() {
		return header.getCharset();
	}
	
}
