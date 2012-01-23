package com.predic8.membrane.servlet;

import java.io.File;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.util.ResourceResolver;

public class WebAppResolver extends ResourceResolver {

	static private Log log = LogFactory.getLog(WebAppResolver.class.getName());

	private String appBase;

	@Override
	public File getRealFile(String uri, boolean useMembraneHome) {
		if (new File(uri).isAbsolute()) {
			log.debug("loading absolute resource: " + uri);
			return new File(uri);
		}

		log.debug("loading resource relative to appBase: " + uri);
		log.debug("appBase: " + appBase);
		return new File(appBase, uri);
	}

	public String getAppBase() {
		return appBase;
	}

	public void setAppBase(String appBase) {
		this.appBase = appBase;
	}

}
