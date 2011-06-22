package com.predic8.membrane.core.interceptor.cbr;

import java.util.*;

public class DefaultRouteProvider implements RouteProvider {

	private List<Route> routes = new ArrayList<Route>();

	
	public void setRoutes(List<Route> routes) {
		this.routes = routes;
	}

	@Override
	public List<Route> getRoutes() {
		
		return routes ;
	}

}
