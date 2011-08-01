package com.predic8.membrane.core.interceptor.cbr;

import java.util.*;

public class DefaultRouteProvider implements RouteProvider {

	private List<Case> routes = new ArrayList<Case>();

	
	public void setRoutes(List<Case> routes) {
		this.routes = routes;
	}

	@Override
	public List<Case> getRoutes() {
		
		return routes ;
	}

}
