package com.predic8.membrane.core.interceptor.balancer;

import java.util.*;

import org.apache.commons.logging.*;

public class ClusterManager {
	private static Log log = LogFactory.getLog(ClusterManager.class.getName());
	
	Map<String, Cluster> clusters = new Hashtable<String, Cluster>();
	long timeout = 0;
	long sessionTimeout = 2*60000;
	
	public ClusterManager() {
		new SessionCleanupThread(clusters, sessionTimeout).start();
		getCluster("Default");
	}
	
	public void up(String cName, String host, int port) {
		getCluster(cName).nodeUp(new Node(host, port));
	}
	
	public void down(String cName, String host, int port) {
		getCluster(cName).nodeDown(new Node(host, port));
	}	
	
	public List<Node> getAllNodesByCluster(String cName) {
		return getCluster(cName).getAllNodes(timeout);
	}

	public List<Node> getAvailableNodesByCluster(String cName) {
		return getCluster(cName).getAvailableNodes(timeout);
	}

	public boolean containsSession(String cName, String sessionId) {
		return getCluster(cName).containsSession(sessionId);
	}
	
	public void addSession2Cluster(String sessionId, String cName, Node n) {
		getCluster(cName).addSession(sessionId, n);
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

	public Map<String, Session> getSessions(String cluster) {
		return getCluster(cluster).getSessions();
	}

	public List<Session> getSessionsByNode(String cName, Node node) {
		return getCluster(cName).getSessionsByNode(node);
	}

	public long getSessionTimeout() {
		return sessionTimeout;
	}

	public void setSessionTimeout(long sessionTimeout) {
		this.sessionTimeout = sessionTimeout;
	}
	
	
}
