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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.http.Response;

public class EtcdResponse {

	private static JsonFactory jsonFac = new JsonFactory();

	private EtcdRequest originalRequest;
	private int statusCode = 0;
	private String body;

	public EtcdRequest getOriginalRequest() {
		return originalRequest;
	}

	public void setOriginalRequest(EtcdRequest originalRequest) {
		this.originalRequest = originalRequest;
	}

	public EtcdResponse(EtcdRequest originalRequest, Response resp) {
		this.originalRequest = originalRequest;
		statusCode = resp.getStatusCode();
		body = resp.getBodyAsStringDecoded();
	}

	private JsonParser getParser(String json) {
		JsonParser result = null;
		try {
			synchronized (jsonFac) {
				result = jsonFac.createParser(json);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	public ArrayList<String> getDirectories() {
		JsonParser par = getParser(body);

		String baseKey = originalRequest.baseKey;
		String module = originalRequest.module;
		ArrayList<String> directories = new ArrayList<String>();
		Map<String, Object> respData = null;
		try {
			respData = new ObjectMapper().readValue(par, Map.class);
		} catch (JsonMappingException e) {
		} catch (IOException e) {
		}
		if (respData.containsKey("node")) {
			LinkedHashMap<String, Object> nodeJson = (LinkedHashMap<String, Object>) respData.get("node");
			if (nodeJson.containsKey("nodes")) {
				ArrayList<Object> nodesArray = (ArrayList<Object>) nodeJson.get("nodes");
				for (Object object : nodesArray) {
					LinkedHashMap<String, Object> dirs = (LinkedHashMap<String, Object>) object;
					if (dirs.containsKey("key")) {
						String servicePath = dirs.get("key").toString();
						String uuid = servicePath.replaceAll(baseKey + module, "");
						directories.add(uuid);
					}
				}
			}
		}
		return directories;
	}

	public String getValue() {
		return get("value");
	}

	@SuppressWarnings("unchecked")
	public String get(String name) {
		JsonParser par = getParser(body);

		String result = null;

		Map<String, Object> respData = null;
		try {
			respData = new ObjectMapper().readValue(par, Map.class);
		} catch (JsonMappingException e) {
		} catch (IOException e) {
		}
		if (respData.containsKey("node")) {
			LinkedHashMap<String, Object> nodeJson = (LinkedHashMap<String, Object>) respData.get("node");
			if (nodeJson.containsKey(name)) {
				result = nodeJson.get(name).toString();
			}
		}

		if (result == null) {
			throw new RuntimeException();
		}

		return result;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(int statuscode) {
		this.statusCode = statuscode;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public boolean is2XX() {
		return checkStatusCode(200,300);
	}

	private boolean isInRange(int minInclusive, int maxExclusive, int value) {
		return value >= minInclusive && value < maxExclusive;
	}

	private boolean checkStatusCode(int minInc, int maxExc) {
		return isInRange(minInc, maxExc, this.getStatusCode());
	}
}
