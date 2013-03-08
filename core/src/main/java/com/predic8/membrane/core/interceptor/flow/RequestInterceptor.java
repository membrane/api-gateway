package com.predic8.membrane.core.interceptor.flow;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;

@MCElement(name="request")
public class RequestInterceptor extends AbstractFlowInterceptor {
	
	private static final Log log = LogFactory.getLog(RequestInterceptor.class);

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		boolean logDebug = log.isDebugEnabled();

		for (Interceptor i : getInterceptors()) {
			Flow f = i.getFlow();
			if (f == Flow.RESPONSE)
				continue;

			if (logDebug)
				log.debug("Invoking request handler: " + i.getDisplayName() + " on exchange: " + exc);

			Outcome o = i.handleRequest(exc);
			if (o != Outcome.CONTINUE)
				return o;
		}
		return Outcome.CONTINUE;
	}
	
	@Override
	protected void writeInterceptor(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement("request");
		for (Interceptor i : getInterceptors())
			i.write(out);
		out.writeEndElement();
	}
	
}
