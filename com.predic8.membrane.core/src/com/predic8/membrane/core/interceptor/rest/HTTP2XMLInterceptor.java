package com.predic8.membrane.core.interceptor.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.xml.Request;
import com.predic8.membrane.core.http.xml.URI;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class HTTP2XMLInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(HTTP2XMLInterceptor.class.getName());

	public HTTP2XMLInterceptor() {
		priority = 140;
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		log.debug("uri: "+ exc.getRequest().getUri());
		
		String res = new Request(exc.getRequest()).toXml();
		log.debug("http-xml: "+ res);

		exc.getRequest().setBodyContent(res.getBytes("UTF-8"));

		// TODO
		exc.getRequest().setMethod("POST");
		exc.getRequest().getHeader().add("SOAPAction", "");

		return Outcome.CONTINUE;
	}

}
