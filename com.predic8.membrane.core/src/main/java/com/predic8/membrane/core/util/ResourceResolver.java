package com.predic8.membrane.core.util;

import java.io.*;

import org.apache.commons.httpclient.URI;
import org.apache.commons.logging.*;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import com.predic8.xml.util.ExternalResolver;

public class ResourceResolver {
	static private Log log = LogFactory
			.getLog(ResourceResolver.class.getName());

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

		if (uri.startsWith("classpath:")) {
			log.debug("loading resource from classpath: " + uri);
			return getClass().getResourceAsStream(uri.substring(10));
		}

		return new FileInputStream(getRealFile(uri, useMembraneHome));
	}

	protected File getRealFile(String uri, boolean useMembraneHome) {

		if (useMembraneHome && !new File(uri).isAbsolute()) {
			log.debug("loading resource relative to MEMBRANE_HOME: " + uri);
			log.debug("MEMBRANE_HOME: " + System.getenv("MEMBRANE_HOME"));
			return new File(System.getenv("MEMBRANE_HOME"), uri);
		}

		log.debug("loading resource from file system relative to cwd: " + uri);
		return new File(uri);
	}
	
	public LSResourceResolver toLSResourceResolver() {
		return new LSResourceResolver() {
			@Override
			public LSInput resolveResource(String type, String namespaceURI,
					String publicId, String systemId, String baseURI) {
				try {
					String file = new URI(new URI(baseURI, false), systemId, false).getPath();
					return new LSInputImpl(publicId, file, resolve(file));
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
