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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.HttpClient;
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
	
	public String combine(String parent, String relativeChild) {
		if (parent.contains(":/")) {
			try {
				return new URI(parent).resolve(relativeChild).toString();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else if (parent.startsWith("/")) {
			try {
				return new URI("file:" + parent).resolve(relativeChild).toString().substring(5);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else {
			return new File(new File(parent).getParent(), relativeChild).getAbsolutePath();
		}
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
	
	public List<String> getChildren(String uri, boolean useMembraneHome) {
	    if(uri.startsWith("http:") || uri.startsWith("https:")) {
	        return null;
	    } 
	    
		if (uri.startsWith("classpath:")) {
			return null;
		}
		
	    if(uri.startsWith("file:")) {
	    	try {
	    		uri = new URL(uri).getPath();
	    	} catch (Exception e) {
	    		throw new RuntimeException(e);
	    	}
	    }
	    String[] children = getRealFile(uri, useMembraneHome).list();
	    if (children == null)
	    	return null;
	    ArrayList<String> res = new ArrayList<String>(children.length);
	    for (String child : children)
	    	res.add(child);
	    return res;
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

	private HttpClient httpClient;
	
	private synchronized HttpClient getHttpClient() {
		if (httpClient == null) {
			httpClient = new HttpClient();
		    //HttpConnectionParams.setConnectionTimeout(httpClient.getParams(), 10000);
		}
		return httpClient;
	}
	
	public static class DownloadException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public DownloadException() {
		}
		
		public DownloadException(String message) {
			super(message);
		}

		public DownloadException(Exception e) {
			super(e);
		}
		
		private int status;
		private String url;
		
		@Override
		public String getMessage() {
			return super.getMessage() + " " + status + " while downloading " + url;
		}
		
		public void setUrl(String url) {
			this.url = url;
		}
		
		public void setStatus(int status) {
			this.status = status;
		}
		
	}
	
	protected InputStream resolveViaHttp(String url) {
		try {
		    Exchange exc = new Request.Builder().method(Request.METHOD_GET).url(url).header(Header.USER_AGENT, Constants.PRODUCT_NAME + " " + Constants.VERSION).buildExchange();
		    Response response = getHttpClient().call(exc);
		    try {
		    	if(response.getStatusCode() != 200) {
		    		DownloadException rde = new DownloadException("could not get resource " + url + " by HTTP");
		    		rde.setStatus(response.getStatusCode());
		    		rde.setUrl(url);
		    		throw rde;
		    	}
		    	return new ByteArrayInputStream(ByteUtil.getByteArrayData(response.getBodyAsStreamDecoded()));
		    } finally {
		    	if (exc.getTargetConnection() != null)
		    		exc.getTargetConnection().close();
		    }
		} catch (ResourceDownloadException e) {
			throw e;
		} catch (Exception e) {
			DownloadException rde = new DownloadException(e);
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
						systemId = new URI(baseURI).resolve(systemId).toString();
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
			
			protected InputStream resolveViaHttp(Object url) {
				return ResourceResolver.this.resolveViaHttp((String) url);
			}
		};
	}
}
