/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import org.apache.commons.logging.*;

public class RouterService implements RouterServiceMBean {

	private static Log log = LogFactory.getLog(RouterService.class.getName());

	Router router;
	
	private String monitorBeansXml="classpath:monitor-beans.xml";
	private String rulesXml="classpath:rules.xml";
	
    public void start() throws Exception {
    	router = Router.init(monitorBeansXml);
		router.getConfigurationManager().loadConfiguration(rulesXml);
        log.info("Router started");
    }

	public void stop() throws Exception {
        router.shutdown();
        log.info("Router stopped");
    }

	public String getMonitorBeansXml() {
		return monitorBeansXml;
	}

	public void setMonitorBeansXml(String monitorBeansXml) {
		this.monitorBeansXml = monitorBeansXml;
	}

	public String getRulesXml() {
		return rulesXml;
	}

	public void setRulesXml(String rulesXml) {
		this.rulesXml = rulesXml;
	}

}
