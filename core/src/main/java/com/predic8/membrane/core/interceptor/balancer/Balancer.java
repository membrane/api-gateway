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

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.AbstractXmlElement;

@MCElement(name="clusters", topLevel=false)
public class Balancer extends AbstractXmlElement {
	public static final String DEFAULT_NAME = "Default";
	private static Log log = LogFactory.getLog(Balancer.class.getName());

	private final Map<String, Cluster> clusters = new Hashtable<String, Cluster>();
	private String name = DEFAULT_NAME;
	private long timeout = 0;
	private SessionCleanupThread sct;

	public Balancer() {
		addCluster(BalancerUtil.getSingleClusterNameOrDefault(this));
		sct = new SessionCleanupThread(clusters);
		sct.start();
	}

	@Override
	protected void finalize() throws Throwable {
		if (sct != null) {
			sct.interrupt();
			sct = null;
		}
		super.finalize();
	}

	public long getSessionTimeout() {
		return sct == null ? 0 : sct.getSessionTimeout();
	}

	public void setSessionTimeout(long sessionTimeout) {
		if (sessionTimeout == 0) {
			if (sct != null) {
				sct.interrupt();
				sct = null;
			}
		} else {
			if (sct == null) {
				sct = new SessionCleanupThread(clusters);
				sct.start();
			}
			sct.setSessionTimeout(sessionTimeout);
		}
	}

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	public List<Cluster> getClusters() {
		return new ArrayList<Cluster>(clusters.values()) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean add(Cluster e) {
				Balancer.this.clusters.put(e.getName(), e);
				return super.add(e);
			}
		};
	}

	private Cluster getCluster(String name) {
		if (!clusters.containsKey(name)) // backward-compatibility: auto create
			// clusters as they are accessed
			addCluster(name);
		return clusters.get(name);
	}

	public boolean addCluster(String name) {
		if (clusters.containsKey(name))
			return false;
		log.debug("adding cluster with name [" + name + "] to balancer ["
				+ name + "]");
		clusters.put(name, new Cluster(name));
		return true;
	}

	/**
	 * @description A list of clusters.
	 */
	@MCChildElement
	public void setClusters(List<Cluster> clusters) {
		this.clusters.clear();
		for (Cluster cluster : clusters)
			this.clusters.put(cluster.getName(), cluster);
	}

	public void up(String cName, String host, int port) {
		getCluster(cName).nodeUp(new Node(host, port));
	}

	public void down(String cName, String host, int port) {
		getCluster(cName).nodeDown(new Node(host, port));
	}

	public void takeout(String cName, String host, int port) {
		getCluster(cName).nodeTakeOut(new Node(host, port));
	}

	public List<Node> getAllNodesByCluster(String cName) {
		return getCluster(cName).getAllNodes(timeout);
	}

	public List<Node> getAvailableNodesByCluster(String cName) {
		return getCluster(cName).getAvailableNodes(timeout);
	}

	public void addSession2Cluster(String sessionId, String cName, Node n) {
		getCluster(cName).addSession(sessionId, n);
	}

	public void removeNode(String cluster, String host, int port) {
		getCluster(cluster).removeNode(new Node(host, port));
	}

	public Node getNode(String cluster, String host, int port) {
		return getCluster(cluster).getNode(new Node(host, port));
	}

	public Map<String, Session> getSessions(String cluster) {
		return getCluster(cluster).getSessions();
	}

	public List<Session> getSessionsByNode(String cName, Node node) {
		return getCluster(cName).getSessionsByNode(node);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}
