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
import java.util.concurrent.atomic.AtomicBoolean;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.balancer.Balancer;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingInterceptor;
import com.predic8.membrane.core.interceptor.balancer.Node;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

@MCElement(name = "etcdBasedConfigurator")
public class EtcdBasedConfigurator implements ApplicationContextAware, Lifecycle, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(EtcdBasedConfigurator.class.getName());

	private ApplicationContext context;
	private int port = 8080;
	private String baseUrl;
	private String baseKey;
	private Router router;
	private HashMap<String, ServiceProxy> runningServiceProxyForModule = new HashMap<>();
	private HashMap<String, HashSet<EtcdNodeInformation>> runningNodesForModule = new HashMap<>();
	private int waitTimeUntilPollAgain = 1000;
	private SSLParser ssl = null;
	private SSLContext sslCtx = null;
	private AtomicBoolean updateThreadRunning = new AtomicBoolean(false);

	private Thread nodeRefreshThread = new Thread(() -> {
		updateThreadRunning.compareAndSet(false,true);
		while (updateThreadRunning.get()) {
			try {
				setUpServiceProxies(getConfigFromEtcd());
				Thread.sleep(waitTimeUntilPollAgain);
			}catch (Exception ignored) {
			}
			if(Thread.interrupted()){
				return;
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
     * @default "<a href="http://localhost:4001">...</a>"
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
		if (ssl != null)
			sslCtx = new StaticSSLContext(ssl, router.getResolverMap(), router.getBaseLocation());

		if (!nodeRefreshThread.isAlive()) {
			nodeRefreshThread.start();
		}
	}

	private void setUpServiceProxies(ArrayList<EtcdNodeInformation> nodes) throws Exception {
		HashSet<EtcdNodeInformation> newRunningNodes = new HashSet<>();
		if (nodes.size() > 0) {
			for (EtcdNodeInformation node : nodes) {
				String currentModule = node.getModule();
				if (!runningServiceProxyForModule.containsKey(currentModule)) {
					setUpModuleServiceProxy(currentModule + " cluster", port, currentModule);
					runningNodesForModule.put(currentModule, new HashSet<>());
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
		HashSet<EtcdNodeInformation> currentlyRunningNodes = new HashSet<>();
		for (String module : runningNodesForModule.keySet()) {
			currentlyRunningNodes.addAll(runningNodesForModule.get(module));
		}
		for (EtcdNodeInformation node : newRunningNodes) {
			currentlyRunningNodes.remove(node);
		}
		for (EtcdNodeInformation node : currentlyRunningNodes) {
			shutdownRunningClusterNode(node);
		}

        HashSet<String> modules = new HashSet<>(runningNodesForModule.keySet());
		for (String module : modules) {
			if (runningNodesForModule.get(module).isEmpty()) {
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

	private EtcdRequest createRequest(String module){
		if(sslCtx != null)
			return EtcdRequest.create(sslCtx, baseUrl, baseKey, module);
		else
			return EtcdRequest.create(baseUrl, baseKey, module);
	}

	private ArrayList<EtcdNodeInformation> getConfigFromEtcd() {
		ArrayList<EtcdNodeInformation> nodes = new ArrayList<>();
		try {
			EtcdResponse respAvailableModules = createRequest("").sendRequest();
			if (!respAvailableModules.is2XX()) {
				return nodes;
			}
			ArrayList<String> availableModules = respAvailableModules.getDirectories();
			for (String module : availableModules) {
				EtcdResponse respAvailableServicesForModule = createRequest(module).sendRequest();
				if (!respAvailableServicesForModule.is2XX()) {
					return nodes;
				}
				ArrayList<String> availableUUIDs = respAvailableServicesForModule.getDirectories();
				for (String uuid : availableUUIDs) {

					EtcdResponse respName = createRequest(module).uuid(uuid)
							.getValue("name").sendRequest();
					if (!respName.is2XX()) {
						return nodes;

					}
					String targetName = respName.getValue();

					EtcdResponse respPort = createRequest(module).uuid(uuid)
							.getValue("port").sendRequest();
					if (!respPort.is2XX()) {
						return nodes;
					}
					String targetPort = respPort.getValue();

					EtcdResponse respHost = createRequest(module).uuid(uuid)
							.getValue("host").sendRequest();
					if (!respHost.is2XX()) {
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
			log.warn("Error retrieving base info from etcd.");
		}

		return nodes;
	}

	@Override
	public void stop() {
		updateThreadRunning.compareAndSet(true,false);
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

	public SSLParser getSsl() {
		return ssl;
	}

	@MCChildElement
	public void setSsl(SSLParser ssl) {
		this.ssl = ssl;
	}

	@Override
	public void destroy() throws Exception {
		log.info("Destroying nodes");
		sslCtx = null;
		ssl = null;
		updateThreadRunning.compareAndSet(true,false);
		nodeRefreshThread.interrupt();
		try {
			nodeRefreshThread.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		nodeRefreshThread = null;
	}
}