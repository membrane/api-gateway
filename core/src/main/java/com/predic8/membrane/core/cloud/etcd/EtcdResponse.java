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
	private Response originalResponse;

	public EtcdRequest getOriginalRequest() {
		return originalRequest;
	}

	public void setOriginalRequest(EtcdRequest originalRequest) {
		this.originalRequest = originalRequest;
	}

	public Response getOriginalResponse() {
		return originalResponse;
	}

	public void setOriginalResponse(Response originalResponse) {
		this.originalResponse = originalResponse;
	}

	public EtcdResponse(EtcdRequest originalRequest, Response resp) {
		this.originalRequest = originalRequest;
		originalResponse = resp;
	}

	private JsonParser getParser(String json) {
		JsonParser result = null;
		try {
			synchronized (jsonFac) {
				result = jsonFac.createParser(json);
			}
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	public ArrayList<String> getDirectories() {
		JsonParser par = getParser(originalResponse.getBodyAsStringDecoded());

		String baseKey = originalRequest.baseKey;
		String module = originalRequest.module;
		ArrayList<String> directories = new ArrayList<String>();
		Map<String, Object> respData = null;
		try {
			respData = new ObjectMapper().readValue(par, Map.class);
		} catch (JsonParseException e) {
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

	@SuppressWarnings("unchecked")
	public String getValue() {
		JsonParser par = getParser(originalResponse.getBodyAsStringDecoded());

		String result = null;

		Map<String, Object> respData = null;
		try {
			respData = new ObjectMapper().readValue(par, Map.class);
		} catch (JsonParseException e) {
		} catch (JsonMappingException e) {
		} catch (IOException e) {
		}
		if (respData.containsKey("node")) {
			LinkedHashMap<String, Object> nodeJson = (LinkedHashMap<String, Object>) respData.get("node");
			if (nodeJson.containsKey("value")) {
				result = nodeJson.get("value").toString();
			}
		}

		if (result == null) {
			throw new RuntimeException();
		}

		return result;
	}

	public void waitForResponse() {
		try {
			originalResponse.readBody();
		} catch (IOException ignored) {
		}
		//originalResponse.getBodyAsStringDecoded();
	}
}
