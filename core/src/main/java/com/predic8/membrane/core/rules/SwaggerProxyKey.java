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

import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.swagger.models.Path;
import io.swagger.models.Swagger;

public class SwaggerProxyKey extends ServiceProxyKey {
	private static Log log = LogFactory.getLog(SwaggerProxyKey.class.getName());

	private Swagger swagger;

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
		// check if request is in Swagger specification
		Map<String, Path> paths = swagger.getPaths();
		for (Entry<String,Path> p : paths.entrySet()) {
			if (pathTemplateMatch(uri, p.getKey()) && methodMatch(method, p.getValue())) {
				log.debug("Request is a Swagger call according to specification");
				return true;
			}
		}
		return false;
	}

	// Self-made Path Template Matching
	private boolean pathTemplateMatch(String calledURI, String specName) {
		final String IDENTIFIER = "[A-Za-z_0-9]+";
		specName = specName.replaceAll("\\{" + IDENTIFIER + "\\}", IDENTIFIER);
		String spec = swagger.getBasePath() + specName;
		return Pattern.matches(spec, calledURI);
	}

	private boolean methodMatch(String method, Path path) {
		return method.equalsIgnoreCase("GET") && path.getGet() != null
			|| method.equalsIgnoreCase("POST") && path.getPost() != null
			|| method.equalsIgnoreCase("HEAD") && path.getHead() != null
			|| method.equalsIgnoreCase("PUT") && path.getPut() != null
			|| method.equalsIgnoreCase("DELETE") && path.getDelete() != null;
	}

	public Swagger getSwagger() {
		return swagger;
	}
	public void setSwagger(Swagger swag) {
		this.swagger = swag;
	}

}
