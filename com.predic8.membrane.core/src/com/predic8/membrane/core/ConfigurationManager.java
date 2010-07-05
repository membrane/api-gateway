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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.io.ConfigurationStore;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class ConfigurationManager {

	private Configuration configuration = new Configuration();

	private ConfigurationStore configurationStore;

	private Router router;

	private List<SecurityConfigurationChangeListener> securityChangeListeners = new Vector<SecurityConfigurationChangeListener>();
	
	protected static Log log = LogFactory.getLog(ConfigurationManager.class.getName());
	
	public void saveConfiguration(String fileName) throws Exception {
		configuration.setRules(router.getRuleManager().getRules());
		configurationStore.write(configuration, fileName);
	}

	private void checkFileExists(String fileName) throws IOException {
		if (fileName == null || "".equals(fileName))
			throw new IOException("File " + fileName + " does not exists");

		File file = new File(fileName);

		if (!file.exists() || !file.isFile())
			throw new IOException("File " + fileName + " does not exists");

	}

	public void loadConfiguration(String fileName) throws Exception {

		checkFileExists(fileName);

		Configuration storedConfiguration = configurationStore.read(fileName);

		configuration.copyFields(storedConfiguration);

		setSecuritySystemProperties();
		Collection<Rule> rules = storedConfiguration.getRules();
		if (rules == null || rules.isEmpty())
			return;

		router.getRuleManager().removeAllRules();
		for (Rule rule : rules) {
			getHttpTransport().addPort(rule.getKey().getPort(), rule.isInboundTSL());
			router.getRuleManager().addRuleIfNew(rule);
			log.debug("Added rule " + rule + " on port " + rule.getKey().getPort());
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

	private HttpTransport getHttpTransport() {
		return ((HttpTransport) router.getTransport());
	}

	public Configuration getConfiguration() {
		return configuration;
	}

	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
	}

	public ConfigurationStore getConfigurationStore() {
		return configurationStore;
	}

	public void setConfigurationStore(ConfigurationStore configurationStore) {
		this.configurationStore = configurationStore;
	}
	
	public String getDefaultConfigurationFile() {
		return System.getProperty("user.home") + System.getProperty("file.separator") + ".membrane.xml";
	}

	public Router getRouter() {
		return router;
	}

	public void setRouter(Router router) {
		this.router = router;
	}
	
	public void addSecurityConfigurationChangeListener(SecurityConfigurationChangeListener listener) {
		if (listener == null)
			return;
		securityChangeListeners.add(listener);
	}

	public void removeSecurityConfigurationChangeListener(SecurityConfigurationChangeListener listener) {
		if (listener == null)
			return;
		securityChangeListeners.remove(listener);
	}
	
}
