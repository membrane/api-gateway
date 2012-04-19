/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.io.ConfigurationFileStore;
import com.predic8.membrane.core.io.ConfigurationStore;
import com.predic8.membrane.core.rules.Rule;

public class ConfigurationManager {

	protected static Log log = LogFactory.getLog(ConfigurationManager.class
			.getName());

	private Proxies proxies;
	private HotDeploymentThread deploymentThread;
	private boolean hotDeploy = true;
	private ConfigurationStore configurationStore = new ConfigurationFileStore();

	private final Router router;
	
	public ConfigurationManager(Router router) {
		this.router = router;
		configurationStore.setRouter(router);
	}

	public void saveConfiguration(String fileName) throws Exception {
		getProxies().setRules(router.getRuleManager().getRules());
		getProxies().write(fileName);
	}

	public void loadConfiguration(String fileName) throws Exception {

		setProxies(configurationStore.read(fileName));

		router.getRuleManager().removeAllRules();

		for (Rule rule : getProxies().getRules()) {
			router.getRuleManager().addProxyIfNew(rule);
		}

		if (!fileName.startsWith("classpath:") && hotDeploy) {
			if (deploymentThread == null) {
				deploymentThread = new HotDeploymentThread(router);
				deploymentThread.setProxiesFile(fileName);
				deploymentThread.start();
			} else {
				deploymentThread.setProxiesFile(fileName);
			}
		}
	}

	public Proxies getProxies() {
		if (proxies == null)
			proxies = new Proxies(router);
		return proxies;
	}

	public void setProxies(Proxies configuration) {
		this.proxies = configuration;
		configuration.setRouter(router);
	}

	public ConfigurationStore getConfigurationStore() {
		return configurationStore;
	}

	public void setConfigurationStore(ConfigurationStore configurationStore) {
		this.configurationStore = configurationStore;
		configurationStore.setRouter(router);
	}

	public String getDefaultConfigurationFile() {
		return System.getProperty("user.home")
				+ System.getProperty("file.separator") + ".membrane.xml";
	}

	public Router getRouter() {
		return router;
	}

	public boolean isHotDeploy() {
		return hotDeploy;
	}

	public void setHotDeploy(boolean hotDeploy) {
		this.hotDeploy = hotDeploy;
	}

	public void stopHotDeployment() {
		if (!hotDeploy)
			return;
		try {
			deploymentThread.interrupt();
			deploymentThread.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
