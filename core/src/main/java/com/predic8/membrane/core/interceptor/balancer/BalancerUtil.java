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

package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;

import java.util.*;

public class BalancerUtil {

	public static List<Cluster> collectClusters(Router router) {
		ArrayList<Cluster> result = new ArrayList<>();
		for (Proxy r : router.getRuleManager().getRules()) {
			List<Interceptor> interceptors = r.getInterceptors();
			if (interceptors != null)
				for (Interceptor i : interceptors)
					if (i instanceof LoadBalancingInterceptor)
						result.addAll(((LoadBalancingInterceptor)i).getClusterManager().getClusters());
		}
		return result;
	}

	public static List<LoadBalancingInterceptor> collectBalancers(Router router) {
		ArrayList<LoadBalancingInterceptor> result = new ArrayList<>();
		for (Proxy r : router.getRuleManager().getRules()) {
			List<Interceptor> interceptors = r.getInterceptors();
			if (interceptors != null)
				for (Interceptor i : interceptors)
					if (i instanceof LoadBalancingInterceptor)
						result.add((LoadBalancingInterceptor)i);
		}
		return result;
	}

	public static Balancer lookupBalancer(Router router, String name) {
		for (Proxy r : router.getRuleManager().getRules()) {
			List<Interceptor> interceptors = r.getInterceptors();
			if (interceptors != null)
				for (Interceptor i : interceptors)
					if (i instanceof LoadBalancingInterceptor)
						if (((LoadBalancingInterceptor)i).getName().equalsIgnoreCase(name))
							return ((LoadBalancingInterceptor) i).getClusterManager();
		}
		throw new RuntimeException("balancer with name \"" + name + "\" not found.");
	}

	public static LoadBalancingInterceptor lookupBalancerInterceptor(Router router, String name) {
		for (Proxy r : router.getRuleManager().getRules()) {
			List<Interceptor> interceptors = r.getInterceptors();
			if (interceptors != null)
				for (Interceptor i : interceptors)
					if (i instanceof LoadBalancingInterceptor)
						if (((LoadBalancingInterceptor)i).getName().equalsIgnoreCase(name))
							return (LoadBalancingInterceptor) i;
		}
		throw new RuntimeException("balancer with name \"" + name + "\" not found.");
	}

	public static boolean hasLoadBalancing(Router router) {
		for (Proxy r : router.getRuleManager().getRules()) {
			List<Interceptor> interceptors = r.getInterceptors();
			if (interceptors == null)
				continue;
			for (Interceptor i : interceptors)
				if (i instanceof LoadBalancingInterceptor)
					return true;
		}
		return false;
	}

	public static void up(Router router, String balancerName, String cName, String host, int port) {
		lookupBalancer(router, balancerName).up(cName, host, port);
	}

	public static void down(Router router, String balancerName, String cName, String host, int port) {
		lookupBalancer(router, balancerName).down(cName, host, port);
	}

	public static void takeout(Router router, String balancerName, String cName, String host, int port) {
		lookupBalancer(router, balancerName).takeout(cName, host, port);
	}

	public static List<Node> getAllNodesByCluster(Router router, String balancerName, String cName) {
		return lookupBalancer(router, balancerName).getAllNodesByCluster(cName);
	}

	public static List<Node> getAvailableNodesByCluster(Router router, String balancerName, String cName) {
		return lookupBalancer(router, balancerName).getAvailableNodesByCluster(cName);
	}

	public static void addSession2Cluster(Router router, String balancerName, String sessionId, String cName, Node n) {
		lookupBalancer(router, balancerName).addSession2Cluster(sessionId, cName, n);
	}

	public static void removeNode(Router router, String balancerName, String cluster, String host, int port) {
		lookupBalancer(router, balancerName).removeNode(cluster, host, port);
	}

	public static Node getNode(Router router, String balancerName, String cluster, String host, int port) {
		return lookupBalancer(router, balancerName).getNode(cluster, host, port);
	}

	public static Map<String, Session> getSessions(Router router, String balancerName, String cluster) {
		return lookupBalancer(router, balancerName).getSessions(cluster);
	}

	public static List<Session> getSessionsByNode(Router router, String balancerName, String cName, Node node) {
		return lookupBalancer(router, balancerName).getSessionsByNode(cName, node);
	}

	public static String getSingleClusterNameOrDefault(Balancer balancer){
		if(balancer.getClusters().size() == 1)
			return balancer.getClusters().getFirst().getName();
		return Cluster.DEFAULT_NAME;
	}

}
