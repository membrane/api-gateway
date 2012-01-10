package com.predic8.membrane.core.interceptor;

import javax.xml.stream.*;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.ErrorResponse;
import com.predic8.membrane.core.http.Response;

public class ThrottleInterceptor extends AbstractInterceptor {
	private Log log = LogFactory.getLog(ThrottleInterceptor.class.getName());
	
	private long delay = 0;
	private int maxThreads = 0;
	private int threads = 0;
	private int busyDelay = 0;
	
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
	
	
	
	
}
