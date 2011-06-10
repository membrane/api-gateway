package com.predic8.membrane.core.interceptor.balancer;

import java.util.*;

import org.apache.commons.logging.*;

public class ClusterManager {
	private static Log log = LogFactory.getLog(ClusterManager.class.getName());
	
	Map<String, Cluster> clusters = new HashMap<String, Cluster>();
	long timeout = 0;
	
	public void up(String cName, String host, int port) {
		getCluster(cName).nodeUp(new Node(host, port));
	}
	
	public void down(String cName, String host, int port) {
		getCluster(cName).nodeDown(new Node(host, port));
	}	
	
	public List<Node> getAllNodes(String cName) {
		return getCluster(cName).getAllNodes(timeout);
	}

	public List<Node> getAvailableNodes(String cName) {
		return getCluster(cName).getAvailableNodes(timeout);
	}
	
	private Cluster getCluster(String name) {
		if ( !clusters.containsKey(name) ) {
			log.info("creating cluster with name ["+name+"]");
			clusters.put(name, new Cluster(name));
		}
		return clusters.get(name);
	}

	public List<Cluster> getClusters() {
		return new LinkedList<Cluster>(clusters.values());
	}

	public boolean addCluster(String name) {		
		if ( clusters.containsKey(name) ) return false;
		log.info("adding cluster with name ["+name+"]");
		clusters.put(name, new Cluster(name));
		return true;
	}

	public void delete(String cluster, String host, int port) {
		getCluster(cluster).removeNode(new Node(host, port));		
	}

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	public Node getNode(String cluster, String host, int port) {
		return getCluster(cluster).getNode(new Node(host,port));		
	}
	
	
}
