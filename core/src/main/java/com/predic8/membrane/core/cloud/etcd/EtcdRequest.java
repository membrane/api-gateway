/* Copyright 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.cloud.etcd;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import com.predic8.membrane.core.exchange.Exchange;
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
	String root = "/v2/keys";
	String baseKey = "";
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
	String longPoll = "";
	String recursiveLongPoll = "";
	String waitIndex = "";

	public EtcdRequest() {
	}

	public EtcdRequest defaultModule() {
		module("/eep");
		return this;
	}

	public EtcdRequest defaultBaseKey() {
		baseKey("/asa/lb");
		return this;
	}

	public EtcdRequest local() {
		ip("localhost").port("4001");
		return this;
	}

	public EtcdRequest url(String url) {
		URL u;
		try {
			u = new URL(url);
			ip(u.getHost()).port(Integer.toString(u.getPort()));
		} catch (MalformedURLException e) {
		}

		return this;
	}

	public EtcdRequest module(String module) {
		this.module = module;
		return this;
	}

	public EtcdRequest baseKey(String baseModule) {
		this.baseKey = baseModule;
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
		this.uuid = uuid;
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
		this.key = dir;
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

	public EtcdRequest longPoll() {
		this.method = MethodType.GET;
		this.longPoll = "wait=true";
		return this;
	}

	public EtcdRequest longPollRecursive() {
		this.recursiveLongPoll = "recursive=true";
		return longPoll();
	}

	public EtcdRequest longPollRecursive(long index) {
		this.waitIndex = "waitIndex=" + index;
		return longPollRecursive();
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
			throw new RuntimeException(e);
		}
		return new EtcdResponse(this, response);
	}

	protected EtcdRequest createPutRequest() {
		StringBuilder builder = new StringBuilder();
		builder.append(httpPrefix).append(ip).append(port).append(root).append(baseKey).append(module).append(uuid)
				.append(key);
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

	private void addQueries(StringBuilder builder, String... queries) {
		if (queries.length == 0) {
			return;
		}
		if (!queries[0].equals("")) {
			builder.append("?" + queries[0]);
		}
		for (int i = 1; i < queries.length; i++) {
			if (!queries[i].equals("")) {
				builder.append("&" + queries[i]);
			}
		}
	}

	protected EtcdRequest createPostRequest() {
		// TODO Auto-generated method stub
		return this;
	}

	protected EtcdRequest createDeleteRequest() {
		StringBuilder builder = new StringBuilder();
		builder.append(httpPrefix).append(ip).append(port).append(root).append(baseKey).append(module).append(uuid);
		/*
		 * if (!deleteDir) { builder.append(key); }
		 */
		url = builder.toString();
		body = "";
		return this;

	}

	protected EtcdRequest createGetRequest() {
		StringBuilder builder = new StringBuilder();
		builder.append(httpPrefix).append(ip).append(port).append(root).append(baseKey).append(module).append(uuid)
				.append(key);
		addQueries(builder, longPoll, recursiveLongPoll, waitIndex);
		url = builder.toString();
		body = "";
		return this;
	}

}
