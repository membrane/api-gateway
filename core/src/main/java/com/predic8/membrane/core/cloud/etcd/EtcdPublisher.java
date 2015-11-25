package com.predic8.membrane.core.cloud.etcd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.transport.http.HttpClient;

@MCElement(name = "etcdPublisher")
public class EtcdPublisher implements ApplicationContextAware, Lifecycle {

	private ApplicationContext context;
	private HashMap<String, ArrayList<String>> modulesToUUIDs = new HashMap<String, ArrayList<String>>();
	private int ttlInSeconds = 20; //300 normally, other for testing
	boolean isRunning = true;
	private String baseUrl;
	private String baseKey;

	public String getBaseUrl() {
		return baseUrl;
	}

	/**
	 * @description URL for etcd
	 * @default "http://localhost:4001"
	 */
	@MCAttribute
	public void setBaseUrl(String baseURL) {
		this.baseUrl = baseURL;
	}

	public String getBaseKey() {
		return baseKey;
	}

	/**
	 * @description Key/Directory
	 * @default "/asa/lb"
	 */
	@MCAttribute
	public void setBaseKey(String baseKey) {
		this.baseKey = baseKey;
	}

	private Thread ttlRefreshThread = new Thread(new Runnable() {

		boolean isFirstTime = true;

		@Override
		public void run() {
			int sleepTime = (ttlInSeconds - 10) * 1000;
			if (isFirstTime) {
				isFirstTime = false;
				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
				}
			}
			while (isRunning) {
				System.out.println("Refreshing ttl");
				for (String module : modulesToUUIDs.keySet()) {
					for (String uuid : modulesToUUIDs.get(module)) {
						EtcdResponse respTTLDirRefresh = EtcdUtil.createBasicRequest(baseUrl, baseKey, module)
								.uuid(uuid).refreshTTL(ttlInSeconds).sendRequest();
						if (!EtcdUtil.checkOK(respTTLDirRefresh)) {
							throw new RuntimeException();
						}
					}
				}
				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
				}
			}
		}

	});

	@Override
	public boolean isRunning() {
		return false;
	}

	@Override
	public void start() {
		Router router = context.getBean(Router.class);
		System.out.println("EtcdPublisher OUTPUT:");
		for (Rule rule : router.getRuleManager().getRules()) {
			if (rule instanceof ServiceProxy) {
				ServiceProxy sp = (ServiceProxy) rule;

				if (sp.getPath() == null) {
					continue;
				}
				String path = sp.getPath().getValue();
				String name = sp.getName();
				String port = Integer.toString(sp.getPort());
				String uuid = "/" + UUID.randomUUID().toString();
				String host = "localhost";

				EtcdResponse respTTLDirCreate = EtcdUtil.createBasicRequest(baseUrl, baseKey, path).createDir(uuid)
						.ttl(ttlInSeconds).sendRequest();
				if (!EtcdUtil.checkOK(respTTLDirCreate)) {
					throw new RuntimeException();
				}

				EtcdResponse respName = EtcdUtil.createBasicRequest(baseUrl, baseKey, path).uuid(uuid)
						.setValue("name", name).sendRequest();
				if (!EtcdUtil.checkOK(respName)) {
					throw new RuntimeException();
				}

				EtcdResponse respPort = EtcdUtil.createBasicRequest(baseUrl, baseKey, path).uuid(uuid)
						.setValue("port", port).sendRequest();
				if (!EtcdUtil.checkOK(respPort)) {
					throw new RuntimeException();
				}

				EtcdResponse respHost = EtcdUtil.createBasicRequest(baseUrl, baseKey, path).uuid(uuid)
						.setValue("host", host).sendRequest();
				if (!EtcdUtil.checkOK(respHost)) {
					throw new RuntimeException();
				}

				if (!modulesToUUIDs.containsKey(path)) {
					modulesToUUIDs.put(path, new ArrayList<String>());
				}
				
				
				System.out.println("UUID: " + uuid);
				
				modulesToUUIDs.get(path).add(uuid);
			}
		}
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				isRunning = false;
				ttlRefreshThread.interrupt();
				for (String module : modulesToUUIDs.keySet()) {
					for (String uuid : modulesToUUIDs.get(module)) {
						EtcdResponse respUnregisterProxy = EtcdUtil.createBasicRequest(baseUrl, baseKey, module)
								.uuid(uuid).deleteDir().sendRequest();
						// this is probably unneeded as the etcd data has ttl set and will autodelete after the ttl
					}
				}
			}
		});
		ttlRefreshThread.start();

	}

	@Override
	public void stop() {

	}

	@Override
	public void setApplicationContext(ApplicationContext arg0) throws BeansException {
		context = arg0;

	}

}
