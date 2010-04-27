package com.predic8.membrane.core.interceptor;

import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.util.HttpUtil;

public class DispatchingInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(DispatchingInterceptor.class.getName());
	
	@Override
	public Outcome handleRequest(Exchange aExc) throws Exception {

		if (!(aExc instanceof HttpExchange))
			return Outcome.CONTINUE;
		
		HttpExchange exc = (HttpExchange)aExc;
		
		if (exc.getRule() instanceof ForwardingRule) {
			
		}

		
		if (exc.getRequest().isCONNECTRequest()) {
			String[] uriParts = HttpUtil.splitConnectUri(exc.getRequest().getUri());
			
		}

		URL url = new URL(exc.getOriginalRequestUri());
		exc.getRequest().getHeader().setHost(url.getHost());
		
		log.debug("PATH: " + url.getPath());

	
		return Outcome.CONTINUE;
	}

}
