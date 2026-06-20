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

import com.google.common.base.Objects;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.proxies.StatisticCollector;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Objects.equal;
import static com.predic8.membrane.core.interceptor.balancer.Node.Status.DOWN;
import static com.predic8.membrane.core.interceptor.balancer.Node.Status.UP;
import static java.lang.System.currentTimeMillis;

/**
 * @description A single backend server in a cluster, addressed by host and port. A node is assumed to be up until a health check or an external command marks it down; while down it
 * receives no requests.
 * @yaml <pre><code>
 * node:
 *   host: node1.predic8.com
 *   port: 8080
 * </code></pre>
 */
@MCElement(name="node", component =false)
public class Node extends AbstractXmlElement {

    public enum Status {
		UP, DOWN, TAKEOUT
	}

	private String host;
	private int port;
	private String healthUrl;

	private int priority = 10;

	// Initialize with a starttime
	private volatile long lastUpTime = System.currentTimeMillis();

	/**
	 * Assume a node is UP until proven DOWN
	 */
	private volatile Status status = UP;

	private final AtomicInteger counter = new AtomicInteger();
	private final AtomicInteger threads = new AtomicInteger();

	private final ConcurrentHashMap<Integer, StatisticCollector> statusCodes = new ConcurrentHashMap<>();

	public Node(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public Node() {
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof Node n &&
			   equal(host, n.host) &&
			   port == n.port;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(host, port);
	}

	public int getLost() {
		int received = 0;
		for ( StatisticCollector statisticCollector : statusCodes.values() ) {
			received += statisticCollector.getCount();
		}
		return counter.get() - received - threads.get();
	}

	public double getErrors() {
		int successes = 0;
		int all = 0;
		for (Map.Entry<Integer, StatisticCollector> e: statusCodes.entrySet() ) {
			int count = e.getValue().getCount();
			all += count;
			if ( e.getKey() < 500 && e.getKey() > 0) {
				successes += count;
			}
		}
		return all == 0 ? 0: 1-(double)successes/all;
	}

	public long getLastUpTime() {
		return lastUpTime;
	}

	public void setLastUpTime(long lastUpTime) {
		this.lastUpTime = lastUpTime;
	}

	public String getHost() {
		return host;
	}

	/**
	 * @description Host name or IP address of the backend server.
	 * @example node1.predic8.com
	 */
	@MCAttribute
	@Required
	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	/**
	 * @description TCP port of the backend server. When left at <code>0</code> the destination URL is built without an
	 * explicit port, i.e. port 80 for HTTP.
	 * @example 8080
	 * @default 0
	 */
	@MCAttribute
	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * @description Full HTTP(S) URL polled to check this node's health. When unset, a plain TCP connection to the host
	 * and port is used instead. Takes effect only when a balancerHealthMonitor is configured.
	 * @example http://localhost:8080/health
	 */
	@MCAttribute
	public void setHealthUrl(String healthUrl) {
		this.healthUrl = healthUrl;
	}

	public String getHealthUrl() {
		return healthUrl;
	}

    /**
     * @description Selection priority used by priorityStrategy; lower numbers are preferred. Ignored by the other
     * dispatching strategies.
     * @default 10
     * @example 2
     */
	@MCAttribute
	public void setPriority(int priority) {
		this.priority = priority;
	}

	public int getPriority() {
		return priority;
	}

	public boolean isUp() {
		return status == UP;
	}

	public boolean isDown() {
		return status == DOWN;
	}

	public boolean isTakeOut() {
		return status == Status.TAKEOUT;
	}

	public void setStatus(Status status) {
		if (status == DOWN) {
			threads.set(0);
		}
		this.status = status;
		if (status == UP) {
			lastUpTime = currentTimeMillis();
		}
	}

	public Status getStatus() {
		return status;
	}

	@Override
	public String toString() {
		return "["+host+":"+port+"]";
	}

	public int getCounter() {
		return counter.get();
	}

	public void incCounter() {
		counter.incrementAndGet();
	}

	public void clearCounter() {
		counter.set(0);
		statusCodes.clear();
	}

	private StatisticCollector getStatisticCollectorByStatusCode(int code) {
		StatisticCollector sc = statusCodes.get(code);
		if (sc == null) {
			sc = new StatisticCollector(true);
			StatisticCollector sc2 = statusCodes.putIfAbsent(code, sc);
			if (sc2 != null)
				sc = sc2;
		}
		return sc;
	}

	public void collectStatisticsFrom(Exchange exc) {
		StatisticCollector sc = getStatisticCollectorByStatusCode(exc.getResponse().getStatusCode());
		synchronized(sc) {
			sc.collectFrom(exc);
		}
	}

	public void addThread() {
		if (!isUp()) return;
		threads.incrementAndGet();
	}

	public void removeThread() {
		if (!isUp()) return;
		threads.decrementAndGet();
	}

	public int getThreads() {
		return threads.get();
	}

	public Map<Integer, StatisticCollector> getStatisticsByStatusCodes() {
		return statusCodes;
	}

	@Override
	public void write(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("node");

		out.writeAttribute("host", host);
		out.writeAttribute("port", ""+port);

		out.writeEndElement();
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) {

		host = token.getAttributeValue("", "host");
		port = Integer.parseInt(token.getAttributeValue("", "port")!=null?token.getAttributeValue("", "port"):"80");
	}

	public String getDestinationURL(Exchange exc) {
		return "http://" + getHost() + (getPort() == 0 ? "" : ":" + getPort()) + exc.getRequest().getUri();
	}

}
