/* Copyright 2009 predic8 GmbH, www.predic8.com

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

import java.net.*;
import java.util.List;

import javax.xml.stream.*;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.HttpUtil;

public class LoadBalancingInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(LoadBalancingInterceptor.class
			.getName());

	// private List<Node> nodes = new LinkedList<Node>();

	private DispatchingStrategy strategy = new RoundRobinStrategy();

	private AbstractSessionIdExtractor sessionIdExtractor;

	private boolean failOver = true;

	public LoadBalancingInterceptor() {
		name = "Balancer";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		log.debug("handleRequest");

		Node dispatchedNode;
		try {
			dispatchedNode = getDispatchedNode(exc.getRequest());
		} catch (EmptyNodeListException e) {
			log.error("No Node found.");
			exc.setResponse(HttpUtil.createResponse(500,
					"Internal Server Error", getErrorPage(), "text/html"));
			return Outcome.ABORT;
		}

		dispatchedNode.incCounter();
		dispatchedNode.addThread();

		exc.setProperty("dispatchedNode", dispatchedNode);

		exc.setOriginalRequestUri(getDestinationURL(dispatchedNode, exc));

		exc.getDestinations().clear();
		exc.getDestinations().add(getDestinationURL(dispatchedNode, exc));

		setFailOverNodes(exc, dispatchedNode);

		return Outcome.CONTINUE;
	}

	private String getErrorPage() {
		return "<html><head><title>Internal Server Error</title></head><body><h1>Internal Server Error</h1></body></html>";
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		log.debug("handleResponse");

		if (sessionIdExtractor != null) {
			String sessionId = getSessionId(exc.getResponse());

			if (sessionId != null) {
				router.getClusterManager().addSession2Cluster(sessionId,
						"Default", (Node) exc.getProperty("dispatchedNode"));
			}
		}

		updateDispatchedNode(exc);

		return Outcome.CONTINUE;
	}

	private void setFailOverNodes(Exchange exc, Node dispatchedNode)
			throws MalformedURLException {
		if (!failOver)
			return;

		for (Node ep : getEndpoints()) {
			if (!ep.equals(dispatchedNode))
				exc.getDestinations().add(getDestinationURL(ep, exc));
		}
	}

	private void updateDispatchedNode(Exchange exc) {
		Node n = (Node) exc.getProperty("dispatchedNode");
		n.removeThread();
		n.addStatusCode(exc.getResponse().getStatusCode());
	}

	private Node getDispatchedNode(Message msg) throws Exception {
		String sessionId;
		if (sessionIdExtractor == null
				|| (sessionId = getSessionId(msg)) == null) {
			log.debug("no session id found.");
			return strategy.dispatch(this);
		}

		Session s = getSession(sessionId);
		if (s == null)
			log.debug("no session found for id " + sessionId);
		if (s == null || s.getNode().isDown()) {
			log.debug("assigning new node for session id " + sessionId
					+ (s != null ? " (old node was " + s.getNode() + ")" : ""));
			router.getClusterManager().addSession2Cluster(sessionId, "Default",
					strategy.dispatch(this));
		}
		s = getSession(sessionId);
		s.used();
		return s.getNode();
	}

	private Session getSession(String sessionId) {
		return router.getClusterManager().getSessions("Default").get(sessionId);
	}

	public String getDestinationURL(Node ep, Exchange exc)
			throws MalformedURLException {
		return "http://" + ep.getHost() + ":" + ep.getPort()
				+ getRequestURI(exc);
	}

	private String getSessionId(Message msg) throws Exception {
		return sessionIdExtractor.getSessionId(msg);
	}

	private String getRequestURI(Exchange exc) throws MalformedURLException {
		if (exc.getOriginalRequestUri().toLowerCase().startsWith("http://")) // TODO
																				// what
																				// about
																				// HTTPS?
			return new URL(exc.getOriginalRequestUri()).getFile();

		return exc.getOriginalRequestUri();
	}

	public DispatchingStrategy getDispatchingStrategy() {
		return strategy;
	}

	public void setDispatchingStrategy(DispatchingStrategy strategy) {
		this.strategy = strategy;
	}

	public List<Node> getEndpoints() {
		return router.getClusterManager().getAvailableNodesByCluster("Default");
	}

	public AbstractSessionIdExtractor getSessionIdExtractor() {
		return sessionIdExtractor;
	}

	public void setSessionIdExtractor(
			AbstractSessionIdExtractor sessionIdExtractor) {
		this.sessionIdExtractor = sessionIdExtractor;
	}

	public boolean isFailOver() {
		return failOver;
	}

	public void setFailOver(boolean failOver) {
		this.failOver = failOver;
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("balancer");

		if (sessionIdExtractor != null) {
			sessionIdExtractor.write(out);
		}

		router.getClusterManager().write(out);

		((AbstractXmlElement) strategy).write(out);

		out.writeEndElement();
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws Exception {
		if (token.getLocalName().equals("xmlSessionIdExtractor")) {
			sessionIdExtractor = new XMLElementSessionIdExtractor();
			sessionIdExtractor.parse(token);
		} else if (token.getLocalName().equals("jSessionIdExtractor")) {
			sessionIdExtractor = new JSESSIONIDExtractor();
			sessionIdExtractor.parse(token);
		} else if (token.getLocalName().equals("clusters")) {
			router.getClusterManager().parse(token);
		} else if (token.getLocalName().equals("byThreadStrategy")) {
			parseByThreadStrategy(token);
		} else if (token.getLocalName().equals("roundRobinStrategy")) {
			parseRoundRobinStrategy(token);
		} else {
			super.parseChildren(token, child);
		}
	}

	private void parseByThreadStrategy(XMLStreamReader token) throws Exception {
		ByThreadStrategy byTStrat = new ByThreadStrategy();
		byTStrat.parse(token);
		strategy = byTStrat;
	}

	private void parseRoundRobinStrategy(XMLStreamReader token)
			throws Exception {
		RoundRobinStrategy rrStrat = new RoundRobinStrategy();
		rrStrat.parse(token);
		strategy = rrStrat;
	}

}
