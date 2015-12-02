package com.predic8.membrane.core.cloud.etcd;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.JSONPObject;
import com.google.api.client.json.JsonObjectParser;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.HttpClient;

public class EtcdRequestTest {

	@Test
	public void testSendRequest() throws Exception {
		/*EtcdResponse respLongPoll = EtcdUtil.createBasicRequest("http://localhost:4001", "/asa/lb", "/eep").uuid("/5ba3349b-86c3-4d6c-9c4e-ae5800bbad12").longPoll().sendRequest();
		System.out.println(respLongPoll.getOriginalRequest().method);
		System.out.println(respLongPoll.getOriginalRequest().url);
		System.out.println(respLongPoll.getOriginalRequest().body);
		System.out.println(respLongPoll.getOriginalResponse().getBodyAsStringDecoded());
		System.out.println("---");*/
		
		EtcdResponse respLongPollRecursive = EtcdUtil.createBasicRequest("http://localhost:4001", "/asa/lb", "").longPollRecursive().sendRequest();
		if(EtcdUtil.checkOK(respLongPollRecursive))
		{
			System.out.println("is ok");
		}
		System.out.println("Waiting");
		respLongPollRecursive.waitForResponse();
		System.out.println("Waiting done");
		System.out.println(respLongPollRecursive.getOriginalRequest().method);
		System.out.println(respLongPollRecursive.getOriginalRequest().url);
		System.out.println(respLongPollRecursive.getOriginalRequest().body);
		System.out.println(respLongPollRecursive.getOriginalResponse().getBodyAsStringDecoded());
		System.out.println("---");
		

		/*
		 * EtcdResponse respPut = new EtcdRequest().client(new
		 * HttpClient()).local().defaultBaseModule().defaultModule().uuid(
		 * Integer.toString(1)).setValue("port", "7000").sendRequest();
		 * System.out.println(respPut.getOriginalRequest().method);
		 * System.out.println(respPut.getOriginalRequest().url);
		 * System.out.println(respPut.getOriginalRequest().body);
		 * System.out.println(respPut.getOriginalResponse().
		 * getBodyAsStringDecoded());
		 * 
		 * 
		 * /*EtcdResponse respGet = new
		 * EtcdRequest().local().defaultBaseModule().defaultModule().uuid(
		 * Integer.toString(1)).getValue("port").sendRequest();
		 * System.out.println(respGet.getOriginalRequest().method);
		 * System.out.println(respGet.getOriginalRequest().url);
		 * System.out.println(respGet.getOriginalResponse().
		 * getBodyAsStringDecoded());
		 * 
		 * EtcdResponse respDirectories = new
		 * EtcdRequest().local().defaultBaseModule().sendRequest();
		 * System.out.println(respDirectories.getOriginalRequest().method);
		 * System.out.println(respDirectories.getOriginalRequest().url);
		 * System.out.println(respDirectories.getOriginalResponse().
		 * getBodyAsStringDecoded());
		 */

		/*
		 * EtcdResponse respTTLDirectory = new
		 * EtcdRequest().local().defaultBaseModule().createDir("asa/lb/eep").ttl
		 * (30) .sendRequest();
		 * System.out.println(respTTLDirectory.getOriginalRequest().method);
		 * System.out.println(respTTLDirectory.getOriginalRequest().url);
		 * System.out.println(respTTLDirectory.getOriginalRequest().body);
		 * System.out.println("---");
		 * System.out.println(respTTLDirectory.getOriginalResponse().
		 * getBodyAsStringDecoded());
		 */

		/*EtcdResponse respDirGet = new EtcdRequest().local().defaultBaseKey().defaultModule().sendRequest();
		JsonFactory fac = new JsonFactory();
		JsonParser par = fac.createParser(respDirGet.getOriginalResponse().getBodyAsStringDecoded());

		String baseKey = "/asa/lb";
		String module = "/eep";
		ArrayList<String> eepServices = new ArrayList<String>();
		Map<String, Object> respData = new ObjectMapper().readValue(par, Map.class);
		if (respData.containsKey("node")) {
			LinkedHashMap<String, Object> nodeJson = (LinkedHashMap<String, Object>) respData.get("node");
			ArrayList<Object> nodesArray = (ArrayList<Object>) nodeJson.get("nodes");
			for (Object object : nodesArray) {
				LinkedHashMap<String, Object> service = (LinkedHashMap<String, Object>) object;
				String servicePath = service.get("key").toString();
				String uuid = servicePath.replaceAll(baseKey + module, "");
				eepServices.add(uuid);
			}
		}
		for (String s : eepServices) {
			System.out.println(s);
		}*/

		/*
		 * EtcdResponse respTTLDirRefresh = new
		 * EtcdRequest().local().defaultBaseModule().module("test").refreshTTL(
		 * 60) .sendRequest();
		 * System.out.println(respTTLDirRefresh.getOriginalRequest().method);
		 * System.out.println(respTTLDirRefresh.getOriginalRequest().url);
		 * System.out.println(respTTLDirRefresh.getOriginalRequest().body);
		 * System.out.println("---");
		 * System.out.println(respTTLDirRefresh.getOriginalResponse().
		 * getBodyAsStringDecoded());
		 */

	}

}
