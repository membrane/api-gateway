package com.predic8.membrane.core.cloud.etcd;

import java.net.URISyntaxException;
import java.util.UUID;

import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.HttpClient;

public class EtcdRequestTest {

	@Test
	public void testSendRequest() throws Exception {
		EtcdRequest reqPut = new EtcdRequest().client(new HttpClient()).local().defaultBaseModule().defaultModule()
				.uuid(Integer.toString(1)).setValue("port", "7000").createPutRequest();
		System.out.println(reqPut.method);
		System.out.println(reqPut.url);
		System.out.println(reqPut.body);
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

		EtcdResponse respTTLDirectory = new EtcdRequest().local().defaultBaseModule().createDir("test").ttl(30)
				.sendRequest();
		System.out.println(respTTLDirectory.getOriginalRequest().method);
		System.out.println(respTTLDirectory.getOriginalRequest().url);
		System.out.println(respTTLDirectory.getOriginalRequest().body);
		System.out.println("---");
		System.out.println(respTTLDirectory.getOriginalResponse().getBodyAsStringDecoded());

		EtcdResponse respTTLDirRefresh = new EtcdRequest().local().defaultBaseModule().module("test").refreshTTL(60)
				.sendRequest();
		System.out.println(respTTLDirRefresh.getOriginalRequest().method);
		System.out.println(respTTLDirRefresh.getOriginalRequest().url);
		System.out.println(respTTLDirRefresh.getOriginalRequest().body);
		System.out.println("---");
		System.out.println(respTTLDirRefresh.getOriginalResponse().getBodyAsStringDecoded());

	}

}
