package com.predic8.membrane.core.rules;

import java.util.Map;
import java.util.Map.Entry;

import io.swagger.models.Path;
import io.swagger.models.Swagger;

public class SwaggerProxyKey extends ServiceProxyKey {

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

		System.out.println("SwaggerProxyKey.complexMatch()...");

		assert swagger != null;

		Map<String, Path> paths = swagger.getPaths();
		// TODO: check if request is in Swagger specification
		for (Entry<String,Path> p : paths.entrySet()) {

			String name = p.getKey();
			Path path = p.getValue();

			System.out.println(name +" "+ path.getParameters() +" "+ method +" "+ uri);

			if (pathTemplateMatch(uri, name) && methodMatch(method, path)) {
				return true;
			}

		}

		//return true; // <-- temporary
		return false; // <-- when above check works
	}

	// TODO: Path Template Matching !!!!!!!!!!
	private boolean pathTemplateMatch(String calledURI, String specName) {
		// TODO: FIX UGLY TEMPORARY WORKAROUND !!!
		return calledURI.equals(swagger.getBasePath() + specName);
	}

	private boolean methodMatch(String method, Path path) {
		return method.equalsIgnoreCase("GET") && path.getGet() != null
			|| method.equalsIgnoreCase("POST") && path.getPost() != null
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
