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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.springframework.beans.factory.annotation.Required;

import com.google.common.base.Objects;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.rules.StatisticCollector;

@MCElement(name="node", group="loadBalancer", global=false)
public class Node extends AbstractXmlElement {

	public static enum Status {
		UP, DOWN, TAKEOUT;
	}
	
	private String host;
	private int port;
	
	private volatile long lastUpTime;
	private volatile Status status;
	private AtomicInteger counter = new AtomicInteger();
	private AtomicInteger threads = new AtomicInteger();
	
	private ConcurrentHashMap<Integer, StatisticCollector> statusCodes = new ConcurrentHashMap<Integer, StatisticCollector>();  
	
	public Node(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public Node() {
	}

	@Override
	public boolean equals(Object obj) {
		return obj!=null && obj instanceof Node &&
			   host.equals(((Node)obj).getHost()) &&
			   port == ((Node)obj).getPort();
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

	@Required
	@MCAttribute
	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	@MCAttribute
	public void setPort(int port) {
		this.port = port;
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
		return "http://" + getHost() + ":" + getPort() + exc.getRequestURI();
	}

}
