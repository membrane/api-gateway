package com.predic8.membrane.core.cloud.etcd;

import java.net.URISyntaxException;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.HttpClient;

public class EtcdRequest {
	enum MethodType {
		GET, PUT, DELETE, POST,
	}

	HttpClient client = new HttpClient();

	public HttpClient getClient() {
		return client;
	}

	public EtcdRequest client(HttpClient client) {
		this.client = client;
		return this;
	}

	final String httpPrefix = "http://";
	final String bodySeperator = "&";
	String ip = "";
	String port = "";
	String baseModule = "";
	String module = "";
	String uuid = "";
	String key = "";
	String value = "";
	String ttl = "";
	MethodType method = MethodType.GET;

	String url = "";
	String body = "";

	// boolean deleteDir = false;
	String isDir = "";
	String prevExist = "";

	public EtcdRequest() {
	}

	// API design, nur zum angucken wie es aussehen soll
	public static void test() {
		EtcdResponse resp1 = new EtcdRequest().uuid("1").setValue("port", "8080").sendRequest();
		EtcdResponse resp2 = new EtcdRequest().uuid("1").getValue("port").sendRequest();

		EtcdResponse resp3 = new EtcdRequest().ip("127.0.0.1").port("4001").baseModule("vs/keys").module("asa/lb/eep")
				.uuid("1").getValue("port").sendRequest();
		EtcdResponse resp4 = new EtcdRequest().local().defaultBaseModule().defaultModule().uuid("1").getValue("port")
				.sendRequest();

	}

	public EtcdRequest defaultModule() {
		module("asa/lb/eep");
		return this;
	}

	public EtcdRequest defaultBaseModule() {
		baseModule("v2/keys");
		return this;
	}

	public EtcdRequest local() {
		ip("localhost").port("4001");
		return this;
	}

	public EtcdRequest module(String module) {
		this.module = "/" + module;
		return this;
	}

	public EtcdRequest baseModule(String baseModule) {
		this.baseModule = "/" + baseModule;
		return this;
	}

	public EtcdRequest port(String port) {
		this.port = ":" + port;
		return this;
	}

	public EtcdRequest ip(String ip) {
		this.ip = ip;
		return this;
	}

	public EtcdRequest uuid(String uuid) {
		this.uuid = "/" + uuid;
		return this;
	}

	public EtcdRequest setValue(String key, String value) {
		this.key = "/" + key;
		this.value = "value=" + value;
		method = MethodType.PUT;
		return this;
	}

	public EtcdRequest getValue(String key) {
		this.key = "/" + key;
		this.value = "";
		method = MethodType.GET;
		return this;
	}

	public EtcdRequest createDir(String dir) {
		this.isDir = "dir=true";
		this.key = "/" + dir;
		this.value = "";
		method = MethodType.PUT;
		return this;
	}

	public EtcdRequest deleteValue(String key) {
		this.key = key;
		this.value = "";
		method = MethodType.DELETE;
		return this;
	}

	public EtcdRequest deleteDir() {
		this.isDir = "dir=true";
		this.value = "";
		method = MethodType.DELETE;
		return this;
	}

	public EtcdRequest ttl(int seconds) {
		this.ttl = "ttl=" + seconds;
		return this;
	}

	public EtcdRequest refreshTTL(int seconds) {
		this.method = MethodType.PUT;
		this.prevExist = "prevExist=true";
		return ttl(seconds);
	}

	public EtcdResponse sendRequest() {
		Exchange requestExc = null;
		try {
			switch (method) {
			case GET:
				createGetRequest();
				requestExc = new Request.Builder().get(url).buildExchange();
				break;
			case DELETE: // TODO body?
				createDeleteRequest();
				requestExc = new Request.Builder().delete(url).buildExchange();
				break;
			case POST:// TODO body?
				createPostRequest();
				requestExc = new Request.Builder().header("Content-Type", "application/x-www-form-urlencoded").post(url)
						.buildExchange();
				break;
			case PUT:
				createPutRequest();
				requestExc = new Request.Builder().header("Content-Type", "application/x-www-form-urlencoded").put(url)
						.body(body).buildExchange();
				break;
			default:
			}
		} catch (URISyntaxException e1) {
			throw new RuntimeException();
		}
		if (requestExc == null) {
			throw new RuntimeException();
		}
		Response response = null;
		try {
			response = client.call(requestExc).getResponse();
		} catch (Exception e) {
			// e.printStackTrace();
			throw new RuntimeException();
		}
		if (response == null) {
			throw new RuntimeException();
		}
		return new EtcdResponse(this, response);
	}

	protected EtcdRequest createPutRequest() {
		StringBuilder builder = new StringBuilder();
		builder.append(httpPrefix).append(ip).append(port).append(baseModule).append(module).append(uuid).append(key);
		url = builder.toString();
		builder.setLength(0);

		addWithLineSeperator(builder, value, ttl, isDir, prevExist);

		body = builder.toString();
		return this;
	}

	private void addWithLineSeperator(StringBuilder builder, String... values) {
		if (values.length == 0) {
			return;
		}
		boolean putSeperator = false;
		for (String val : values) {
			if (putSeperator) {
				if (val.equals("")) {
					continue;
				} else {
					builder.append(bodySeperator);
					putSeperator = false;
				}
			}
			if (!val.equals("")) {
				builder.append(val);
				putSeperator = true;
			}
		}
	}

	protected EtcdRequest createPostRequest() {
		// TODO Auto-generated method stub
		return this;
	}

	protected EtcdRequest createDeleteRequest() {
		StringBuilder builder = new StringBuilder();
		builder.append(httpPrefix).append(ip).append(port).append(baseModule).append(module).append(uuid);
		/*
		 * if (!deleteDir) { builder.append(key); }
		 */
		url = builder.toString();
		body = "";
		return this;

	}

	protected EtcdRequest createGetRequest() {
		StringBuilder builder = new StringBuilder();
		builder.append(httpPrefix).append(ip).append(port).append(baseModule).append(module).append(uuid).append(key);
		url = builder.toString();
		body = "";
		return this;
	}

}
