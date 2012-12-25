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

package com.predic8.membrane.core.interceptor;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.annot.MCInterceptor;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;

@MCInterceptor(xsd="	<xsd:element name=\"throttle\">\r\n" + 
		"		<xsd:complexType>\r\n" + 
		"			<xsd:complexContent>\r\n" + 
		"				<xsd:extension base=\"beans:identifiedType\">\r\n" + 
		"					<xsd:sequence />\r\n" + 
		"					<xsd:attribute name=\"delay\" type=\"xsd:long\" default=\"0\"/>\r\n" + 
		"					<xsd:attribute name=\"maxThreads\" type=\"xsd:int\" default=\"0\"/>\r\n" + 
		"					<xsd:attribute name=\"busyDelay\" type=\"xsd:int\" default=\"0\"/>\r\n" + 
		"				</xsd:extension>\r\n" + 
		"			</xsd:complexContent>\r\n" + 
		"		</xsd:complexType>\r\n" + 
		"	</xsd:element>\r\n" + 
		"")
public class ThrottleInterceptor extends AbstractInterceptor {
	private static Log log = LogFactory.getLog(ThrottleInterceptor.class.getName());
	
	private long delay = 0;
	private int maxThreads = 0;
	private int threads = 0;
	private int busyDelay = 0;
	
	public ThrottleInterceptor() {
		name = "Throttle";
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if ( delay > 0 ) {
			log.debug("delaying for "+delay+"ms");
			Thread.sleep(delay);
		}
		if ( maxThreads > 0 && threads >= maxThreads ) {
			log.debug("Max thread limit of "+maxThreads+" reached. Waiting "+busyDelay+"ms");
			Thread.sleep(busyDelay);
			if ( threads >= maxThreads ) {
				log.info("Max thread limit of " +maxThreads+ " reached. Server Busy.");
				exc.setResponse(Response.serverUnavailable("Server busy.").build());
				return Outcome.ABORT;
			}
		}
		increaseThreads();		
		log.debug("thread count increased: "+threads);		
		return Outcome.CONTINUE;
	}

	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		decreaseThreads();
		log.debug("thread count decreased: "+threads);
		return Outcome.CONTINUE;
	}
	
	@Override
	public void handleAbort(Exchange exchange) {
		decreaseThreads();
		log.debug("thread count decreased: "+threads);
	}


	private synchronized void decreaseThreads() {
		--threads;		
	}

	private synchronized void increaseThreads() {
		++threads;		
	}

	public long getDelay() {
		return delay;
	}

	public void setDelay(long delay) {
		this.delay = delay;
	}

	public int getMaxThreads() {
		return maxThreads;
	}

	public void setMaxThreads(int maxThreads) {
		this.maxThreads = maxThreads;
	}
		
	public int getBusyDelay() {
		return busyDelay;
	}

	public void setBusyDelay(int busyDelay) {
		this.busyDelay = busyDelay;
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {
		
		out.writeStartElement("throttle");
		
		out.writeAttribute("maxThreads", ""+maxThreads);		
		out.writeAttribute("delay", ""+delay);		
		out.writeAttribute("busyDelay", ""+busyDelay);
		
		out.writeEndElement();
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) {
		
		if ( token.getAttributeValue("", "delay") != null )
			delay = Long.parseLong(token.getAttributeValue("", "delay"));

		if ( token.getAttributeValue("", "maxThreads") != null )
			maxThreads = Integer.parseInt(token.getAttributeValue("", "maxThreads"));

		if ( token.getAttributeValue("", "busyDelay") != null )
			busyDelay = Integer.parseInt(token.getAttributeValue("", "busyDelay"));
	}
	
	
	@Override
	public String getShortDescription() {
		if (delay > 0 || maxThreads > 0)
			return "Throttles the rate of incoming requests.";
		else
			return "Not configured.";
	}
	
	@Override
	public String getLongDescription() {
		StringBuilder sb = new StringBuilder();
		if (delay > 0)
			sb.append("Delays requests by " + String.format("%.1f", delay/1000.0) + " seconds.");
		if (maxThreads > 0) {
			sb.append("Only allows " + maxThreads + " concurrent requests.");
			if (busyDelay > 0)
				sb.append("The server waits at most " + 
				String.format("%.1f", busyDelay/1000.0) + " seconds for enough running requests to terminate, " +
						"returning an error if the server is still busy after the timeout.");
		}
		return sb.toString();
	}
	
	@Override
	public String getHelpId() {
		return "throttle";
	}

}
