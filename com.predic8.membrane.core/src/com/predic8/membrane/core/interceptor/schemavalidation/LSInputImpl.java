package com.predic8.membrane.core.interceptor.schemavalidation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.ls.LSInput;

import com.predic8.membrane.core.Constants;

public class LSInputImpl implements LSInput {

	private static Log log = LogFactory.getLog(LSInputImpl.class.getName());
	
	private String publicId;

	private String systemId;

	private InputStream inputStream;
	
	public LSInputImpl(String publicId, String sysId, String input) {
	    this.publicId = publicId;
	    this.systemId = sysId;
	    this.inputStream = new ByteArrayInputStream(input.getBytes());
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
		return new String(bytes);
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
