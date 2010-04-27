package com.predic8.membrane.core.exchange;

import java.net.MalformedURLException;
import java.net.URL;

import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ProxyRule;

public class ExchangesUtil {

	public static String getServer(Exchange exc) {
		if (exc.getRule() instanceof ProxyRule) {
			try {
				if (exc.getRequest().isCONNECTRequest()) {
					return exc.getRequest().getHeader().getHost();
				}
				
				return new URL(exc.getOriginalRequestUri()).getHost();
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
			return exc.getOriginalRequestUri();
		}
		if (exc.getRule() instanceof ForwardingRule) {
			return ((ForwardingRule) exc.getRule()).getTargetHost();
		}
		return "";
	}
	
	private String extractContentTypeValue(String contentType) {
		if (contentType == null)
			return "";
		int index = contentType.indexOf(";");
		if (index > 0) {
			return contentType.substring(0, index);
		}
		return contentType;
	}
}
