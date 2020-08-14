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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.ls.LSInput;

import com.predic8.membrane.core.Constants;

public class LSInputImpl implements LSInput {

	private static Logger log = LoggerFactory.getLogger(LSInputImpl.class.getName());

	private String publicId;
	private String systemId;
	private InputStream inputStream;

	public LSInputImpl(String publicId, String systemId, InputStream inputStream) {
		this.publicId = publicId;
		this.systemId = systemId;
		this.inputStream = inputStream;
	}

	@Override
	public String getPublicId() {
		return publicId;
	}

	@Override
	public void setPublicId(String publicId) {
		this.publicId = publicId;
	}

	@Override
	public String getBaseURI() {
		return null;
	}

	@Override
	public InputStream getByteStream() {
		return null;
	}

	@Override
	public boolean getCertifiedText() {
		return false;
	}

	@Override
	public Reader getCharacterStream() {
		return null;
	}

	@Override
	public String getEncoding() {
		return Constants.UTF_8;
	}

	@Override
	public String getStringData() {
		synchronized (inputStream) {
			String result = "";
			try {
				result = streamToString();
			} catch (IOException e) {
				log.error("Unable to read stream: " + e);
			}
			return result;
		}
	}

	private String streamToString() throws IOException {
		byte[] bytes = new byte[inputStream.available()];
		inputStream.read(bytes);
		return new String(bytes, Constants.UTF_8_CHARSET);
	}

	@Override
	public void setBaseURI(String baseURI) {
		//ignore
	}

	@Override
	public void setByteStream(InputStream byteStream) {
		this.inputStream = byteStream;
	}

	@Override
	public void setCertifiedText(boolean certifiedText) {
		//ignore
	}

	@Override
	public void setCharacterStream(Reader characterStream) {
		//ignore
	}

	@Override
	public void setEncoding(String encoding) {
		//ignore
	}

	@Override
	public void setStringData(String stringData) {
		//ignore
	}

	@Override
	public String getSystemId() {
		return systemId;
	}

	@Override
	public void setSystemId(String systemId) {
		this.systemId = systemId;
	}

}
