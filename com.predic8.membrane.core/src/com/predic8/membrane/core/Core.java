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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.predic8.membrane.core.exchangestore.ExchangeStore;
import com.predic8.membrane.core.transport.Transport;

public class Core {

	private static XmlBeanFactory beanFactory;

	private static Log log = LogFactory.getLog(Core.class.getName());

	public static void init(String configFileName) {
		log.debug("loading spring config: " + configFileName);
		init(new ClassPathResource(configFileName));
	}

	public static void init(Resource resource) {
		log.debug("loading spring config: " + resource);
		beanFactory = new XmlBeanFactory(resource);
	}

	public static RuleManager getRuleManager() {
		return (RuleManager) beanFactory.getBean("ruleManager");
	}

	public static ExchangeStore getExchangeStore() {
		return (ExchangeStore) beanFactory.getBean("exchangeStore");
	}

	public static Transport getTransport() {
		return (Transport) beanFactory.getBean("transport");
	}

	public static ConfigurationManager getConfigurationManager() {
		return (ConfigurationManager) beanFactory.getBean("configurationManager");
	}
	
}
