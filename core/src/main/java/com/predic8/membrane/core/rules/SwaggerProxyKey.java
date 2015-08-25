package com.predic8.membrane.core.rules;

import java.util.Map;
import java.util.Map.Entry;

import io.swagger.models.Path;
import io.swagger.models.Swagger;

public class SwaggerProxyKey extends ServiceProxyKey {


	private Swagger swagger;


	public SwaggerProxyKey(int port) {
		super(port);
		System.out.println("SwaggerProxyKey CTOR");
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

		System.out.println("real complexMatch called");

		// TODO: check if request is in Swagger specification
		assert swagger != null;

		Map<String, Path> paths = swagger.getPaths();
		for (Entry<String,Path> p : paths.entrySet()) {
			String name = p.getKey();
			Path path = p.getValue();
			System.out.println(name + path);
		}

		return true;
	}

	public Swagger getSwagger() {
		return swagger;
	}
	public void setSwagger(Swagger swag) {
		this.swagger = swag;
	}

}
