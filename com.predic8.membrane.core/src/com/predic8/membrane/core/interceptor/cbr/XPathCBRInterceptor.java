package com.predic8.membrane.core.interceptor.cbr;

import java.io.*;
import java.util.*;

import javax.xml.xpath.*;

import org.apache.commons.logging.*;
import org.xml.sax.InputSource;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.*;
import static com.predic8.membrane.core.util.SynchronizedXPathFactory.*;

public class XPathCBRInterceptor extends AbstractInterceptor {
	private static Log log = LogFactory.getLog(XPathCBRInterceptor.class.getName());
	
	private List<Route> routes = new ArrayList<Route>();
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (exc.getRequest().isBodyEmpty()) {
			return Outcome.CONTINUE;
		}
		
		Route r = findRoute(exc.getRequest());		
		if (r == null) {
			return Outcome.CONTINUE;
		}
		log.debug("match found for {"+r.getxPath()+"} routing to {"+ r.getUrl() + "}");
		
		updateDestination(exc, r);
		return Outcome.CONTINUE;
	}

	private void updateDestination(Exchange exc, Route r) {
		exc.setOriginalRequestUri(r.getUrl());		
		exc.getDestinations().clear();
		exc.getDestinations().add(r.getUrl());
	}

	private Route findRoute(Request request) throws Exception {
		for (Route r : routes) {
			//TODO getBodyAsStream creates ByteArray each call. That could be a performance issue. Using BufferedInputStream did't work, because stream got closed.
			if ( (Boolean) newXPath().evaluate(r.getxPath(), new InputSource(request.getBodyAsStream()), XPathConstants.BOOLEAN) ) 
				return r;
			log.debug("no match found for xpath {"+r.getxPath()+"}");
		}
		return null;
	}

	public List<Route> getRoutes() {
		return routes;
	}

	public void setRoutes(List<Route> routes) {
		this.routes = routes;
	}
	
	
}
