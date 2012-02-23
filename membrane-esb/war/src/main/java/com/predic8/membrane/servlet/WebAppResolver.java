package com.predic8.membrane.servlet;

import java.io.*;

import javax.servlet.ServletContext;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.util.ResourceResolver;

public class WebAppResolver extends ResourceResolver {

	static private Log log = LogFactory.getLog(WebAppResolver.class.getName());

	private ServletContext ctx;

	@Override
	protected InputStream resolveFile(String uri, boolean useMembraneHome)
			throws FileNotFoundException {
		log.debug("loading resource from: " + uri);
		return ctx.getResourceAsStream(uri);
	}

	public ServletContext getCtx() {
		return ctx;
	}

	public void setCtx(ServletContext ctx) {
		this.ctx = ctx;
	}

}
