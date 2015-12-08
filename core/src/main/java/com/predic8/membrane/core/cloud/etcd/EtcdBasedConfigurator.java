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
import com.predic8.membrane.core.interceptor.balancer.Balancer;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingInterceptor;
import com.predic8.membrane.core.interceptor.balancer.Node;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

@MCElement(name = "etcdBasedConfigurator")
public class EtcdBasedConfigurator implements ApplicationContextAware, Lifecycle {

	private static final Log log = LogFactory.getLog(EtcdBasedConfigurator.class.getName());

	private ApplicationContext context;
	private int port = 8080;
	private String baseUrl;
	private String baseKey;
	private Router router;
	private HashMap<String, ServiceProxy> runningServiceProxyForModule = new HashMap<String, ServiceProxy>();
	private HashMap<String, HashSet<EtcdNodeInformation>> runningNodesForModule = new HashMap<String, HashSet<EtcdNodeInformation>>();
	private int waitTimeUntilPollAgain = 1000;

	private Thread nodeRefreshThread = new Thread(new Runnable() {
		@Override
		public void run() {
			while (true) {
				// System.out.println("Refreshing nodes");
				try {
					setUpServiceProxies(getConfigFromEtcd());
					Thread.sleep(waitTimeUntilPollAgain);
				} catch (Exception ignored) {
				}
			}
		}

	});

	public int getPort() {
		return port;
	}

	/**
	 * @description port
	 * @default 8080
	 */
	@MCAttribute
	public void setPort(int port) {
		this.port = port;
	}

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

	@Override
	public boolean isRunning() {
		return false;
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
		try {
		} catch (Exception ignored) {
		}
		if (!nodeRefreshThread.isAlive()) {
			nodeRefreshThread.start();
		}
	}

	private void setUpServiceProxies(ArrayList<EtcdNodeInformation> nodes) throws Exception {
		HashSet<EtcdNodeInformation> newRunningNodes = new HashSet<EtcdNodeInformation>();
		if (nodes.size() > 0) {
			for (EtcdNodeInformation node : nodes) {
				String currentModule = node.getModule();
				if (!runningServiceProxyForModule.containsKey(currentModule)) {
					setUpModuleServiceProxy(currentModule + " cluster", port, currentModule);
					runningNodesForModule.put(currentModule, new HashSet<EtcdNodeInformation>());
				}
				if (!runningNodesForModule.get(currentModule).contains(node)) {
					setUpClusterNode(node);
				}
				newRunningNodes.add(node);
			}
		}
		cleanUpNotRunningNodes(newRunningNodes);
	}

	private void setUpClusterNode(EtcdNodeInformation node) {
		log.info("Creating " + node);
		ServiceProxy sp = runningServiceProxyForModule.get(node.getModule());
		LoadBalancingInterceptor lbi = (LoadBalancingInterceptor) sp.getInterceptors().get(0);
		lbi.getClusterManager().getClusters().get(0)
				.nodeUp(new Node(node.getTargetHost(), Integer.parseInt(node.getTargetPort())));
		runningNodesForModule.get(node.getModule()).add(node);
	}

	private ServiceProxy setUpModuleServiceProxy(String name, int port, String path) {
		log.info("Creating serviceProxy for module: " + path);
		ServiceProxyKey key = new ServiceProxyKey("*", "*", path, port);
		key.setUsePathPattern(true);
		key.setPathRegExp(false);
		ServiceProxy sp = new ServiceProxy(key, null, 0);

		sp.getInterceptors().add(new LoadBalancingInterceptor());

		try {
			sp.init(router);
			router.add(sp);
			runningServiceProxyForModule.put(path, sp);
		} catch (Exception ignored) {
		}
		return sp;
	}

	private void cleanUpNotRunningNodes(HashSet<EtcdNodeInformation> newRunningNodes) {
		HashSet<EtcdNodeInformation> currentlyRunningNodes = new HashSet<EtcdNodeInformation>();
		for (String module : runningNodesForModule.keySet()) {
			currentlyRunningNodes.addAll(runningNodesForModule.get(module));
		}
		for (EtcdNodeInformation node : newRunningNodes) {
			currentlyRunningNodes.remove(node);
		}
		for (EtcdNodeInformation node : currentlyRunningNodes) {
			shutdownRunningClusterNode(node);
		}

		HashSet<String> modules = new HashSet<String>();
		for (String module : runningNodesForModule.keySet()) {
			modules.add(module);
		}
		for (String module : modules) {
			if (runningNodesForModule.get(module).size() == 0) {
				runningNodesForModule.remove(module);
				shutDownRunningModuleServiceProxy(module);
			}
		}
	}

	private void shutDownRunningModuleServiceProxy(String module) {
		log.info("Destroying serviceProxy for module: " + module);
		ServiceProxy sp = runningServiceProxyForModule.get(module);
		router.getRuleManager().removeRule(sp);
		runningServiceProxyForModule.remove(module);
	}

	private void shutdownRunningClusterNode(EtcdNodeInformation node) {
		log.info("Destroying " + node);
		ServiceProxy sp = runningServiceProxyForModule.get(node.getModule());
		LoadBalancingInterceptor lbi = (LoadBalancingInterceptor) sp.getInterceptors().get(0);
		lbi.getClusterManager().removeNode(Balancer.DEFAULT_NAME, baseUrl, port);
		runningNodesForModule.get(node.getModule()).remove(node);
	}

	private ArrayList<EtcdNodeInformation> getConfigFromEtcd() {
		ArrayList<EtcdNodeInformation> nodes = new ArrayList<EtcdNodeInformation>();
		try {

			EtcdResponse respAvailableModules = EtcdUtil.createBasicRequest(baseUrl, baseKey, "").sendRequest();
			if (!EtcdUtil.checkOK(respAvailableModules)) {
				return nodes;
			}
			ArrayList<String> availableModules = respAvailableModules.getDirectories();
			for (String module : availableModules) {
				EtcdResponse respAvailableServicesForModule = EtcdUtil.createBasicRequest(baseUrl, baseKey, module)
						.sendRequest();
				if (!EtcdUtil.checkOK(respAvailableServicesForModule)) {
					return nodes;
				}
				ArrayList<String> availableUUIDs = respAvailableServicesForModule.getDirectories();
				for (String uuid : availableUUIDs) {

					EtcdResponse respName = EtcdUtil.createBasicRequest(baseUrl, baseKey, module).uuid(uuid)
							.getValue("name").sendRequest();
					if (!EtcdUtil.checkOK(respName)) {
						return nodes;

					}
					String targetName = respName.getValue();

					EtcdResponse respPort = EtcdUtil.createBasicRequest(baseUrl, baseKey, module).uuid(uuid)
							.getValue("port").sendRequest();
					if (!EtcdUtil.checkOK(respPort)) {
						return nodes;
					}
					String targetPort = respPort.getValue();

					EtcdResponse respHost = EtcdUtil.createBasicRequest(baseUrl, baseKey, module).uuid(uuid)
							.getValue("host").sendRequest();
					if (!EtcdUtil.checkOK(respHost)) {
						return nodes;
					}
					String targetHost = respHost.getValue();

					EtcdNodeInformation node = new EtcdNodeInformation(module, uuid, targetHost, targetPort,
							targetName);
					if (node.isValid()) {
						nodes.add(node);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.warn("Error retrieving base info from etcd.");
		}
		return nodes;
	}

	@Override
	public void stop() {
		nodeRefreshThread.interrupt();
		try {
			nodeRefreshThread.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

	}

	@Override
	public void setApplicationContext(ApplicationContext arg0) throws BeansException {
		context = arg0;
	}

	public Router getRouter() {
		return router;
	}

	public void setRouter(Router router) {
		this.router = router;
	}
}