/* Copyright 2009 predic8 GmbH, www.predic8.com

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

import java.util.List;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.io.*;
import com.predic8.membrane.core.rules.Rule;

public class ConfigurationManager {

	protected static Log log = LogFactory.getLog(ConfigurationManager.class.getName());
	
	private Configuration configuration;

	private ConfigurationStore configurationStore = new ConfigurationFileStore();

	private Router router;

	private List<SecurityConfigurationChangeListener> securityChangeListeners = new Vector<SecurityConfigurationChangeListener>();
	
	public void saveConfiguration(String fileName) throws Exception {
		getConfiguration().setRules(router.getRuleManager().getRules());
		getConfiguration().write(fileName);
	}


	public void loadConfiguration(String fileName) throws Exception {

		setConfiguration(configurationStore.read(fileName));

		setSecuritySystemProperties();
		
		router.getRuleManager().removeAllRules();
		
		for (Rule rule : getConfiguration().getRules()) {
			router.getRuleManager().addRuleIfNew(rule);
		}

	}

	public void setSecuritySystemProperties() {
		if (getConfiguration().getKeyStoreLocation() != null)
			System.setProperty("javax.net.ssl.keyStore", getConfiguration().getKeyStoreLocation());
		
		if (getConfiguration().getKeyStorePassword() != null)
			System.setProperty("javax.net.ssl.keyStorePassword", getConfiguration().getKeyStorePassword());
		
		if (getConfiguration().getTrustStoreLocation() != null)
			System.setProperty("javax.net.ssl.trustStore", getConfiguration().getTrustStoreLocation());
		
		if (getConfiguration().getTrustStorePassword() != null)
			System.setProperty("javax.net.ssl.trustStorePassword", getConfiguration().getTrustStorePassword());
	
		notifySecurityChangeListeners();
	}
	
	private void notifySecurityChangeListeners() {
		for (SecurityConfigurationChangeListener listener : securityChangeListeners) {
			try {
				listener.securityConfigurationChanged();
			} catch (Exception e) {
				securityChangeListeners.remove(listener);
			}
		}
	}

	public Configuration getConfiguration() {
		if ( configuration==null ) configuration = new Configuration(router);
		return configuration;
	}

	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
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
		return System.getProperty("user.home") + System.getProperty("file.separator") + ".membrane.xml";
	}

	public Router getRouter() {
		return router;
	}

	public void setRouter(Router router) {
		this.router = router;		
		configurationStore.setRouter(router);
		getConfiguration().setRouter(router);
	}
	
	public void addSecurityConfigurationChangeListener(SecurityConfigurationChangeListener listener) {
		securityChangeListeners.add(listener);
	}

	public void removeSecurityConfigurationChangeListener(SecurityConfigurationChangeListener listener) {
		securityChangeListeners.remove(listener);
	}
	
}
