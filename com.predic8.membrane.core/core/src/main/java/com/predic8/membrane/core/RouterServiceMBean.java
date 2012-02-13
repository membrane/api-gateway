package com.predic8.membrane.core;

public interface RouterServiceMBean {
    public void start() throws Exception;
    public void stop() throws Exception;
	public void setRulesXml(String rulesXml);
	public String getRulesXml();
	public void setMonitorBeansXml(String monitorBeansXml);
	public String getMonitorBeansXml();
}
