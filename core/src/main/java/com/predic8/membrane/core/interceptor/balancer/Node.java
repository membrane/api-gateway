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
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.proxies.*;

import javax.xml.stream.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@MCElement(name="node", topLevel=false)
public class Node extends AbstractXmlElement {

	public enum Status {
		UP, DOWN, TAKEOUT;
	}

	private String host;
	private int port;
	private String healthUrl;
	private int priority = 10;

	private volatile long lastUpTime;
	private volatile Status status;
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
		return obj instanceof Node &&
                host.equals(((Node) obj).getHost()) &&
                port == ((Node) obj).getPort();
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
	 * @description The node's host.
	 * @example server3
	 */
	@Required
	@MCAttribute
	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	/**
	 * @description The node's port.
	 * @example 8080
	 * @default 80
	 */
	@MCAttribute
	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * @description Sets the node's health-check URL.  If not set, the default URL derived from host and port will be used.
	 * @param healthUrl the full HTTP(s) endpoint for this node's health check
	 * @example &lt;node host="localhost" port="8080" healthUrl="http://localhost:8080/health"/&gt;
	 */
	@MCAttribute
	public void setHealthUrl(String healthUrl) {
		this.healthUrl = healthUrl;
	}

	public String getHealthUrl() {
		return healthUrl;
	}

	/**
	 * @description Determines this node's priority within the cluster. Lower values mean higher priority.
	 */
	@MCAttribute
	public void setPriority(int priority) {
		this.priority = priority;
	}

	public int getPriority() {
		return priority;
	}

	public boolean isUp() {
		return status == Status.UP;
	}

	public boolean isDown() {
		return status == Status.DOWN;
	}

	public boolean isTakeOut() {
		return status == Status.TAKEOUT;
	}

	public void setStatus(Status status) {
		if (status == Status.DOWN)
			threads.set(0);
		this.status = status;
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
		threads.incrementAndGet();
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
