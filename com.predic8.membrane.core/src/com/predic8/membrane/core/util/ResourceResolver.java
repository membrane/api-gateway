package com.predic8.membrane.core.util;

import java.io.*;

import org.apache.commons.logging.*;

public class ResourceResolver {
	static private Log log = LogFactory
			.getLog(ResourceResolver.class.getName());

	public InputStream resolve(String uri) throws FileNotFoundException {
		return resolve(uri, false);
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
}
