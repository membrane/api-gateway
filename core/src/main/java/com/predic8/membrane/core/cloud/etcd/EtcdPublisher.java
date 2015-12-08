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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.cloud.ExponentialBackoff;
import com.predic8.membrane.core.cloud.ExponentialBackoff.Job;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;

@MCElement(name = "etcdPublisher")
public class EtcdPublisher implements ApplicationContextAware, Lifecycle {
	private static final Log log = LogFactory.getLog(EtcdPublisher.class.getName());

	private ApplicationContext context;
	private HashMap<String, ArrayList<String>> modulesToUUIDs = new HashMap<String, ArrayList<String>>();
	private HashSet<EtcdNodeInformation> nodesFromConfig = new HashSet<EtcdNodeInformation>();
	private int ttlInSeconds = 20; // 300 normally, other for testing
	private String baseUrl;
	private String baseKey;
	private Router router;
	private int retryDelayMin = 10 * 1000;
	private int retryDelayMax = 10 * 60 * 1000;
	private double expDelayFactor = 2.0d;
	private Job jobPublishToEtcd = new Job() {

		@Override
		public boolean run() throws Exception {
			return publishToEtcd();
		}
	};

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

		int sleepTime = (ttlInSeconds - 10) * 1000;

		@Override
		public void run() {
			try {
				while (true) {
					boolean connectionLost = false;
					// System.out.println("Refreshing ttl");
					for (String module : modulesToUUIDs.keySet()) {
						for (String uuid : modulesToUUIDs.get(module)) {
							try
							{
							EtcdResponse respTTLDirRefresh = EtcdUtil.createBasicRequest(baseUrl, baseKey, module)
									.uuid(uuid).refreshTTL(ttlInSeconds).sendRequest();
							if (!EtcdUtil.checkOK(respTTLDirRefresh)) {
								log.warn("Could not contact etcd at " + baseUrl);
								connectionLost = true;
							}
							}
							catch(Exception e)
							{
								connectionLost = true;
							}
						}
					}
					if (connectionLost) {
						log.warn("Connection lost to etcd");
						ExponentialBackoff.retryAfter(retryDelayMin, retryDelayMax, expDelayFactor,
								"Republish from thread after failed ttl refresh", jobPublishToEtcd);
					}
					Thread.sleep(sleepTime);
				}
			} catch (InterruptedException ignored) {
			}
		}

	});

	@Override
	public boolean isRunning() {
		return false;
	}

	public void readConfig() {
		nodesFromConfig.clear();
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
				nodesFromConfig.add(new EtcdNodeInformation(path, uuid, host, port, name));
			}
		}
	}

	public boolean publishToEtcd() {
		for (EtcdNodeInformation node : nodesFromConfig) {
			String path = node.module;
			String uuid = node.uuid;
			String name = node.name;
			String port = node.targetPort;
			String host = node.targetHost;
			EtcdResponse respTTLDirCreate = EtcdUtil.createBasicRequest(baseUrl, baseKey, path).createDir(uuid)
					.ttl(ttlInSeconds).sendRequest();
			if (!EtcdUtil.checkOK(respTTLDirCreate)) {
				return false;
			}

			EtcdResponse respName = EtcdUtil.createBasicRequest(baseUrl, baseKey, path).uuid(uuid)
					.setValue("name", name).sendRequest();
			if (!EtcdUtil.checkOK(respName)) {
				return false;
			}

			EtcdResponse respPort = EtcdUtil.createBasicRequest(baseUrl, baseKey, path).uuid(uuid)
					.setValue("port", port).sendRequest();
			if (!EtcdUtil.checkOK(respPort)) {
				return false;
			}

			EtcdResponse respHost = EtcdUtil.createBasicRequest(baseUrl, baseKey, path).uuid(uuid)
					.setValue("host", host).sendRequest();
			if (!EtcdUtil.checkOK(respHost)) {
				return false;
			}

			if (!modulesToUUIDs.containsKey(path)) {
				modulesToUUIDs.put(path, new ArrayList<String>());
			}
			modulesToUUIDs.get(path).add(uuid);
		}
		return true;
	}

	@EventListener({ ContextRefreshedEvent.class })
	@Override
	public void start() {
		if (router == null) {
			if (context == null)
				throw new IllegalStateException(
						"EtcdBasedConfigurator requires a Router. Option 1 is to call setRouter(). Option 2 is setApplicationContext() and the EBC will try to use the only Router available.");
			router = context.getBean(Router.class);
		}
		readConfig();
		try {
			ExponentialBackoff.retryAfter(retryDelayMin, retryDelayMax, expDelayFactor, "Publish to etcd",
					jobPublishToEtcd);
		} catch (InterruptedException ignored) {
		}
		if(!ttlRefreshThread.isAlive())
		{
			ttlRefreshThread.start();
		}
	}

	@Override
	public void stop() {
		ttlRefreshThread.interrupt();
		try {
			ttlRefreshThread.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		for (String module : modulesToUUIDs.keySet()) {
			for (String uuid : modulesToUUIDs.get(module)) {
				@SuppressWarnings("unused")
				EtcdResponse respUnregisterProxy = EtcdUtil.createBasicRequest(baseUrl, baseKey, module).uuid(uuid)
						.deleteDir().sendRequest();
				// this is probably unneeded as the etcd data has ttl
				// set and will autodelete after the ttl
			}
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext arg0) throws BeansException {
		context = arg0;

	}

}
