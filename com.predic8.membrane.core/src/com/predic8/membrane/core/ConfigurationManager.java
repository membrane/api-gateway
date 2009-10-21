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

import com.predic8.membrane.core.io.ConfigurationStore;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.Transport;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class ConfigurationManager {
	
	private Configuration configuration;

	private boolean adjustContentLength;

	private boolean adjustHostHeader;

	private boolean indentMessage;

	private boolean trackExchange;

	private ConfigurationStore configurationStore;

	public RuleManager ruleManager;

	public Transport transport;

	public void init() {
		configuration = getDefaultConfiguration();
	}

	public void saveConfiguration(String fileName) {
		configuration.setRules(ruleManager.getRules());

		try {
			configurationStore.write(configuration, fileName);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void checkFile(String fileName) throws IOException {
		if (fileName == null || "".equals(fileName))
			throw new IOException("File " + fileName + " does not exists");

		File file = new File(fileName);

		if (!file.exists() || !file.isFile())
			throw new IOException("File " + fileName + " does not exists");

	}

	public void loadConfiguration(String fileName) throws Exception {

		checkFile(fileName);

		Configuration storedConfiguration = configurationStore.read(fileName);

		configuration.setAdjustHostHeader(storedConfiguration.getAdjustHostHeader());
		configuration.setIndentMessage(storedConfiguration.getIndentMessage());
		configuration.setTrackExchange(storedConfiguration.getTrackExchange());

		Collection<Rule> rules = storedConfiguration.getRules();
		if (rules != null && rules.size() > 0) {
			ruleManager.removeAllRules();
			for (Rule rule : rules) {
				try {
					if (!((HttpTransport) transport).isAnyThreadListeningAt(rule.getRuleKey().getPort())) {
						((HttpTransport) transport).addPort(rule.getRuleKey().getPort());
					}
					ruleManager.addRuleIfNew(rule);
				} catch (Exception e1) {
					throw e1;
				}
			}
		}

	}

	public Configuration getDefaultConfiguration() {
		Configuration config = new Configuration();
		config.setAdjustHostHeader(adjustHostHeader);
		config.setTrackExchange(trackExchange);
		config.setIndentMessage(indentMessage);
		return config;
	}

	public Configuration getConfiguration() {
		return configuration;
	}

	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
	}

	public Transport getTransport() {
		return transport;
	}

	public void setTransport(Transport transport) {
		this.transport = transport;
	}

	public RuleManager getRuleManager() {
		return ruleManager;
	}

	public void setRuleManager(RuleManager ruleManager) {
		this.ruleManager = ruleManager;
	}

	public ConfigurationStore getConfigurationStore() {
		return configurationStore;
	}

	public void setConfigurationStore(ConfigurationStore configurationStore) {
		this.configurationStore = configurationStore;
	}

	public boolean isAdjustContentLength() {
		return adjustContentLength;
	}

	public void setAdjustContentLength(boolean adjustContentLength) {
		this.adjustContentLength = adjustContentLength;
	}

	public boolean isAdjustHostHeader() {
		return adjustHostHeader;
	}

	public void setAdjustHostHeader(boolean adjustHostHeader) {
		this.adjustHostHeader = adjustHostHeader;
	}

	public boolean isIndentMessage() {
		return indentMessage;
	}

	public void setIndentMessage(boolean indentMessage) {
		this.indentMessage = indentMessage;
	}

	public boolean isTrackExchange() {
		return trackExchange;
	}

	public void setTrackExchange(boolean trackExchange) {
		this.trackExchange = trackExchange;
	}

	public String getDefaultConfigurationFile() {
		return System.getProperty("user.home")+ "/.membrane.xml"; 
	}
	
	
}
