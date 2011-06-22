package com.predic8.membrane.core.interceptor.cbr;

public class Route {

	private String url;
	private String xPath;
	
	public Route() {}
	
	public Route(String xPath, String url) {
		this.url = url;
		this.xPath = xPath;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getxPath() {
		return xPath;
	}

	public void setxPath(String xPath) {
		this.xPath = xPath;
	}
	
	

}
