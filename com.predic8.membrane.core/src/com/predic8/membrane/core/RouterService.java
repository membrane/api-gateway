package com.predic8.membrane.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class RouterService implements RouterServiceMBean {

	private static Log log = LogFactory.getLog(RouterService.class.getName());

	Router router;
	
	private String monitorBeansXml="classpath:monitor-beans.xml";
	private String rulesXml="classpath:rules.xml";
	
    public void start() throws Exception {
    	startRouter();
        log.info("Router started");
    }

	public void stop() throws Exception {
        router.getTransport().closeAll();
        log.info("Router stopped");
    }

	private void startRouter() throws Exception {
		router = Router.init(monitorBeansXml);
		router.getConfigurationManager().loadConfiguration(rulesXml);
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
