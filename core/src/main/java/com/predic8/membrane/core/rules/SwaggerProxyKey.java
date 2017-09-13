/* Copyright 2009, 2011, 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.rules;

import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.models.Path;
import io.swagger.models.Swagger;

public class SwaggerProxyKey extends ServiceProxyKey {
	private static Logger log = LoggerFactory.getLogger(SwaggerProxyKey.class.getName());

	private Swagger swagger;
	private boolean allowUI;

	public SwaggerProxyKey(int port) {
		super(port);
	}

	public SwaggerProxyKey(int port, String ip) {
		super(port, ip);
	}

	public SwaggerProxyKey(String host, String method, String path, int port, String ip) {
		super(host, method, path, port, ip);
	}

	public SwaggerProxyKey(String host, String method, String path, int port) {
		super(host, method, path, port);
	}

	@Override
	public boolean complexMatch(String hostHeader, String method, String uri, String version, int port, String localIP) {
		if (swagger == null) {
			log.error("Swagger specification is null!");
			return false;
		}

		// check if swagger specification is fetched
		if (uri.endsWith("swagger.json")) {
			return true;
		}

		// check if request is part of the UI
		if (allowUI && method.equalsIgnoreCase("GET") && isUI(uri)) {
			return true;
		}

		// check if request is in Swagger specification
		Map<String, Path> paths = swagger.getPaths();
		for (Entry<String, Path> p : paths.entrySet()) {
			if (pathTemplateMatch(uri, p.getKey()) && methodMatch(method, p.getValue())) {
				log.debug("Request is a Swagger call according to specification");
				return true;
			}
		}

		return false;
	}

	// Self-made Path Template Matching
	private boolean pathTemplateMatch(String calledURI, String specName) {
		final String IDENTIFIER = "[-_a-zA-Z0-9]+";
		specName = specName.replaceAll("\\{" + IDENTIFIER + "\\}", IDENTIFIER);
		String spec = swagger.getBasePath() + specName;
		
		if (calledURI.contains("?")){
 			calledURI = calledURI.substring(0,calledURI.indexOf("?"));
		}
		
		return Pattern.matches(spec, calledURI);
	}

	private boolean methodMatch(String method, Path path) {
		return method.equalsIgnoreCase("GET") && path.getGet() != null
			|| method.equalsIgnoreCase("POST") && path.getPost() != null
			|| method.equalsIgnoreCase("HEAD") && path.getHead() != null
			|| method.equalsIgnoreCase("PUT") && path.getPut() != null
			|| method.equalsIgnoreCase("DELETE") && path.getDelete() != null;
	}

	private boolean isUI(String path) {
		return Arrays.asList(
			  "/"
			, "/favicon.ico"
			, "/swagger-ui.js"
			, "/css/typography.css"
			, "/css/reset.css"
			, "/css/screen.css"
			, "/css/print.css"
			, "/lib/jquery.slideto.min.js"
			, "/lib/jquery-1.8.0.min.js"
			, "/lib/jquery.wiggle.min.js"
			, "/lib/jquery.ba-bbq.min.js"
			, "/lib/underscore-min.js"
			, "/lib/handlebars-2.0.0.js"
			, "/lib/backbone-min.js"
			, "/lib/highlight.7.3.pack.js"
			, "/lib/marked.js"
			, "/lib/swagger-oauth.js"
			, "/images/favicon-16x16.png"
			, "/images/logo_small.png"
			, "/fonts/droid-sans-v6-latin-700.woff2"
		).contains(path);
	}

	public Swagger getSwagger() {
		return swagger;
	}
	public void setSwagger(Swagger swag) {
		this.swagger = swag;
	}

	public boolean isAllowUI() {
		return allowUI;
	}
	public void setAllowUI(boolean allowUI) {
		this.allowUI = allowUI;
	}

}
