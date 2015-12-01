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
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
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
	private HashSet<EtcdNodeInformation> runningNodes = new HashSet<EtcdNodeInformation>();
	private HashMap<EtcdNodeInformation, ServiceProxy> runningProxies = new HashMap<EtcdNodeInformation, ServiceProxy>();
	private Thread nodeRefreshThread = new Thread(new Runnable() {
		@Override
		public void run() {
			while (true) {
				// System.out.println("Refreshing nodes");
				EtcdResponse respWaitForChange = EtcdUtil.createBasicRequest(baseUrl, baseKey, "").longPollRecursive()
						.sendRequest();
				if (!EtcdUtil.checkOK(respWaitForChange)) {
					log.warn("Could not contact etcd at " + baseUrl);
					// TODO: service proxies loeschen, nach timeout wieder versuchen
					//throw new RuntimeException();
				}
				handleContextRefresh(null);
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

	@Override
	public void start() {
		router = context.getBean(Router.class);
		// System.out.println("Starting configurator");
		handleContextRefresh(null);
	}

	// @EventListener
	public void handleContextRefresh(ContextRefreshedEvent event) {
		setUpServiceProxies(getConfigFromEtcd());
		if (!nodeRefreshThread.isAlive()) {
			nodeRefreshThread.start();
		}
	}

	private void setUpServiceProxies(ArrayList<EtcdNodeInformation> nodes) {
		HashSet<EtcdNodeInformation> newRunningNodes = new HashSet<EtcdNodeInformation>();
		for (EtcdNodeInformation node : nodes) {

			if (!runningNodes.contains(node)) {
				setUpServiceProxy(node);
			}
			newRunningNodes.add(node);
		}
		cleanUpNotRunningUuuids(newRunningNodes);
		runningNodes = newRunningNodes;
	}

	private void cleanUpNotRunningUuuids(HashSet<EtcdNodeInformation> newRunningNodes) {
		for (EtcdNodeInformation node : runningNodes) {
			if (!newRunningNodes.contains(node)) {
				shutDownRunningServiceProxy(node);
			}
		}
	}

	private void shutDownRunningServiceProxy(EtcdNodeInformation node) {
		ServiceProxy sp = runningProxies.get(node);
		router.getRuleManager().removeRule(sp);
	}

	private void setUpServiceProxy(EtcdNodeInformation node) {
		ServiceProxy sp = new ServiceProxy(new ServiceProxyKey("*", "*", node.getModule(), port), node.getTargetHost(),
				node.getTargetPort());
		try {
			router.add(sp);
			runningProxies.put(node, sp);
		} catch (Exception e) {
			// e.printStackTrace();
		}
	}

	private ArrayList<EtcdNodeInformation> getConfigFromEtcd() {
		ArrayList<EtcdNodeInformation> nodes = new ArrayList<EtcdNodeInformation>();

		EtcdResponse respAvailableModules = EtcdUtil.createBasicRequest(baseUrl, baseKey, "").sendRequest();
		if (!EtcdUtil.checkOK(respAvailableModules)) {
			throw new RuntimeException();
		}
		ArrayList<String> availableModules = respAvailableModules.getDirectories();
		for (String module : availableModules) {
			EtcdResponse respAvailableServicesForModule = EtcdUtil.createBasicRequest(baseUrl, baseKey, module)
					.sendRequest();
			if (!EtcdUtil.checkOK(respAvailableServicesForModule)) {
				throw new RuntimeException();
			}
			ArrayList<String> availableUUIDs = respAvailableServicesForModule.getDirectories();
			for (String uuid : availableUUIDs) {

				EtcdResponse respName = EtcdUtil.createBasicRequest(baseUrl, baseKey, module).uuid(uuid)
						.getValue("name").sendRequest();
				if (!EtcdUtil.checkOK(respName)) {
					throw new RuntimeException();
				}
				String targetName = respName.getValue();

				EtcdResponse respPort = EtcdUtil.createBasicRequest(baseUrl, baseKey, module).uuid(uuid)
						.getValue("port").sendRequest();
				if (!EtcdUtil.checkOK(respPort)) {
					throw new RuntimeException();
				}
				int targetPort = Integer.parseInt(respPort.getValue());

				EtcdResponse respHost = EtcdUtil.createBasicRequest(baseUrl, baseKey, module).uuid(uuid)
						.getValue("host").sendRequest();
				if (!EtcdUtil.checkOK(respHost)) {
					throw new RuntimeException();
				}
				String targetHost = respHost.getValue();
				EtcdNodeInformation node = new EtcdNodeInformation(module, uuid, targetHost, targetPort, targetName);
				nodes.add(node);
			}
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

}