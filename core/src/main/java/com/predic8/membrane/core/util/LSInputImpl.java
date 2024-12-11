/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

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

import org.apache.commons.io.input.*;
import org.slf4j.*;
import org.w3c.dom.ls.*;

import java.io.*;

import static java.nio.charset.StandardCharsets.*;

public class LSInputImpl implements LSInput {

	private static final Logger log = LoggerFactory.getLogger(LSInputImpl.class.getName());

	private String publicId;
	private String systemId;
	private InputStream inputStream;

	public LSInputImpl(String publicId, String systemId, InputStream inputStream) throws IOException {
		this.publicId = publicId;
		this.systemId = systemId;
		this.inputStream = BOMInputStream.builder()
				.setInputStream(inputStream)
				.setInclude(false)
				.get();
	}

	public String getPublicId() {
		return publicId;
	}

	public void setPublicId(String publicId) {
		this.publicId = publicId;
	}

	public String getBaseURI() {
		return null;
	}

	public InputStream getByteStream() {
		return null;
	}

	public boolean getCertifiedText() {
		return false;
	}

	public Reader getCharacterStream() {
		return null;
	}

	public String getEncoding() {
		return UTF_8.name();
	}

	public String getStringData() {
		synchronized (inputStream) {
			try {
				return streamToString();
			} catch (IOException e) {
				log.error("Unable to read stream: " + e);
				return "";
			}
		}
	}

	private String streamToString() throws IOException {
		return new String(inputStream.readAllBytes(), UTF_8);
	}

	public void setBaseURI(String baseURI) {
		//ignore
	}

	public void setByteStream(InputStream byteStream) {
		this.inputStream = byteStream;
	}

	public void setCertifiedText(boolean certifiedText) {
		//ignore
	}

	public void setCharacterStream(Reader characterStream) {
		//ignore
	}

	public void setEncoding(String encoding) {
		//ignore
	}

	public void setStringData(String stringData) {
		//ignore
	}

	public String getSystemId() {
		return systemId;
	}

	public void setSystemId(String systemId) {
		this.systemId = systemId;
	}

}
