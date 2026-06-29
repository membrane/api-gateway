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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.interceptor.balancer.Node.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.util.*;

/**
 * @description A named group of nodes within a balancer that share session state. Sticky sessions are tracked at the
 * cluster level, so a session stays on its node as long as that node is up. Each node carries a status of UP, DOWN, or
 * TAKEOUT; only UP nodes receive traffic.
 * @yaml <pre><code>
 * balancer:
 *   name: DemoBalancer
 *   clusters:
 *     - name: PROD
 *       nodes:
 *         - host: node1.predic8.com
 *           port: 8080
 *         - host: node2.predic8.com
 *           port: 8090
 * </code></pre>
 */
@MCElement(name="cluster", component =false)
public class Cluster {

	private static final Logger log = LoggerFactory.getLogger(Cluster.class.getName());

	public static final String DEFAULT_NAME = "Default";

	private String name = DEFAULT_NAME;
	private final List<Node> nodes = Collections.synchronizedList(new LinkedList<>());
	private final Map<String, Session> sessions = new Hashtable<>();

	public Cluster() {
	}

	public Cluster(String name) {
		this.name = name;
	}


	public void nodeUp(Node n) {
		log.debug("node: " + n +" up");
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

	public void removeNode(Node node) {
		nodes.remove(node);
	}

	public List<Node> getAvailableNodes(long timeout) {
		List<Node> l = new LinkedList<>();
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
		return new ArrayList<>(nodes) {
			@Serial
			private static final long serialVersionUID = 1L;

			@Override
			public boolean add(Node e) {
				nodes.add(e);
				return super.add(e);
			}
		};
	}

    /**
     * @description The backend nodes that make up this cluster.
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
	 * @description Name of the cluster, used to address it from the administration interface and the lbclient.
	 * @example PROD
	 * @default Default
	 */
	@MCAttribute
	public void setName(String name) {
		this.name = name;
	}

	public void addSession(String sessionId, Node n) {
		sessions.put(sessionId, new Session(sessionId, n));
	}

	public Map<String, Session> getSessions() {
		return sessions;
	}

	public List<Session> getSessionsByNode(Node node) {
		List<Session> l = new LinkedList<>();
		synchronized (sessions) {
			for (Session s : sessions.values()) {
				if ( s.getNode().equals(node))
					l.add(s);
			}
		}
		return l;
	}

}
