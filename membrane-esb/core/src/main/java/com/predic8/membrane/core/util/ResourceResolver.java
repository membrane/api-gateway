package com.predic8.membrane.core.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import com.predic8.membrane.core.Constants;
import com.predic8.xml.util.ExternalResolver;
import com.predic8.xml.util.ResourceDownloadException;

public class ResourceResolver {
	public InputStream resolve(String uri) throws FileNotFoundException {
		return resolve(uri, false);
	}
	
	public long getTimestamp(String uri) {
		if (uri.startsWith("classpath:"))
			return 0;

		return getRealFile(uri, false).lastModified();
	}

	public InputStream resolve(String uri, boolean useMembraneHome)
			throws FileNotFoundException {
	    if(uri.startsWith("http:") || uri.startsWith("https:")) {
	        return resolveViaHttp(uri);
	    } 
	    
		if (uri.startsWith("classpath:")) {
			return getClass().getResourceAsStream(uri.substring(10));
		}
		
	    if(uri.startsWith("file:")) {
	    	try {
	    		uri = new URL(uri).getPath();
	    	} catch (Exception e) {
	    		throw new RuntimeException(e);
	    	}
	    }
	    return resolveFile(uri, useMembraneHome);
	}

	protected InputStream resolveFile(String uri, boolean useMembraneHome) throws FileNotFoundException {
		return new FileInputStream(getRealFile(uri, useMembraneHome));
	}

	protected File getRealFile(String uri, boolean useMembraneHome) {

		if (useMembraneHome && !new File(uri).isAbsolute()) {
			return new File(System.getenv("MEMBRANE_HOME"), uri);
		}

		return new File(uri);
	}
	
	protected InputStream resolveViaHttp(String url) {
		try{
		    HttpClient client = new HttpClient();
		    client.getHttpConnectionManager().getParams().setConnectionTimeout(10000);
				      
		    HttpMethod method = new GetMethod(url);
		    method.getParams().setParameter(HttpMethodParams.USER_AGENT, "Membrane " + Constants.VERSION);
		    int status = client.executeMethod(method);
		    if(status != 200) {
		    	ResourceDownloadException rde = new ResourceDownloadException("could not get resource " + url + " by HTTP");
		    	rde.setStatus(status);
		    	rde.setUrl(url);
		    	method.releaseConnection();
		    	throw rde;
		    }
		    InputStream is = new ByteArrayInputStream(method.getResponseBody());
		    method.releaseConnection();
		    return is;
		} catch (Exception e) {
			ResourceDownloadException rde = new ResourceDownloadException();
			rde.setRootCause(e);
			rde.setUrl(url);
			throw rde;
		}
	}

	
	public LSResourceResolver toLSResourceResolver() {
		return new LSResourceResolver() {
			@Override
			public LSInput resolveResource(String type, String namespaceURI,
					String publicId, String systemId, String baseURI) {
				try {
					if (!systemId.contains("://"))
						systemId = new URI(new URI(baseURI, false), systemId, false).getPath();
					return new LSInputImpl(publicId, systemId, resolve(systemId));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};
	}

	public ExternalResolver toExternalResolver() {
		return new ExternalResolver() {
			@Override
			public InputStream resolveAsFile(String filename, String baseDir) {
				try {
					if(baseDir != null) {
						return ResourceResolver.this.resolve(baseDir+filename);
					}
					return ResourceResolver.this.resolve(filename);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};
	}
}
