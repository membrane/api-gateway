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

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.interceptor.balancer.Node.Status;

@MCElement(name="cluster", topLevel=false)
public class Cluster {

	private static Logger log = LoggerFactory.getLogger(Cluster.class.getName());

	public static final String DEFAULT_NAME = "Default";

	private String name = DEFAULT_NAME;
	private List<Node> nodes = Collections.synchronizedList(new LinkedList<Node>());
	private Map<String, Session> sessions = new Hashtable<String, Session>();

	public Cluster() {
	}

	public Cluster(String name) {
		this.name = name;
	}


	public void nodeUp(Node n) {
		log.debug("node: " + n +" up");
		getNodeCreateIfNeeded(n).setLastUpTime(System.currentTimeMillis());
		getNodeCreateIfNeeded(n).setStatus(Status.UP);
	}

	public void nodeDown(Node n) {
		log.debug("node: " + n +" down");
		getNodeCreateIfNeeded(n).setStatus(Status.DOWN);
	}

	public void nodeTakeOut(Node n) {
		log.debug("node: " + n +" takeout");
		getNodeCreateIfNeeded(n).setStatus(Status.TAKEOUT);
	}

	public boolean removeNode(Node node) {
		return nodes.remove(node);
	}

	public List<Node> getAvailableNodes(long timeout) {
		List<Node> l = new LinkedList<Node>();
		synchronized (nodes) {
			for (Node n : getAllNodes(timeout)) {
				if ( n.isUp() ) l.add(n);
			}
		}
		return l;
	}

	public List<Node> getAllNodes(long timeout) {
		if (timeout <= 0) {
			return nodes;
		}
		synchronized (nodes) {
			for (Node n : nodes) {
				if ( System.currentTimeMillis()-n.getLastUpTime() > timeout ) n.setStatus(Status.DOWN);
			}
		}
		return nodes;
	}

	public Node getNode(Node ep) {
		synchronized (nodes) {
			return nodes.get(nodes.indexOf(ep));
		}
	}

	private Node getNodeCreateIfNeeded(Node ep) {
		if ( nodes.contains(ep) ) {
			return getNode(ep);
		}
		log.debug("creating endpoint: "+ep);
		nodes.add(new Node(ep.getHost(), ep.getPort()));
		return getNode(ep);
	}

	public List<Node> getNodes() {
		return new ArrayList<Node>(nodes) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean add(Node e) {
				nodes.add(e);
				return super.add(e);
			}
		};
	}

	/**
	 * @description Specifies a node.
	 */
	@MCChildElement
	public void setNodes(List<Node> nodes) {
		this.nodes.clear();
		this.nodes.addAll(nodes);
	}

	public String getName() {
		return name;
	}

	/**
	 * @description Sets the name of the cluster.
	 * @default Default
	 */
	@MCAttribute
	public void setName(String name) {
		this.name = name;
	}

	public boolean containsSession(String sessionId) {
		return sessions.containsKey(sessionId) && sessions.get(sessionId).getNode().isUp();
	}

	public void addSession(String sessionId, Node n) {
		sessions.put(sessionId, new Session(sessionId, n));
	}

	public Map<String, Session> getSessions() {
		return sessions;
	}

	public List<Session> getSessionsByNode(Node node) {
		List<Session> l = new LinkedList<Session>();
		synchronized (sessions) {
			for (Session s : sessions.values()) {
				if ( s.getNode().equals(node))
					l.add(s);
			}
		}
		return l;
	}

}
