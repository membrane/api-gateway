package com.predic8.membrane.core.exchange;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ProxyRule;

public class ExchangesUtil {

	public static final NumberFormat FORMATTER = NumberFormat.getInstance();
	public static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy.MM.dd hh:mm:ss");
	
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
	
	public static String extractContentTypeValue(String contentType) {
		if (contentType == null)
			return "";
		int index = contentType.indexOf(";");
		if (index > 0) {
			return contentType.substring(0, index);
		}
		return contentType;
	}
	
	public static String getStatusCode(Exchange exc) {
		if (exc.getResponse() == null)
			return "";
		return "" + exc.getResponse().getStatusCode();
	}

	public static String getTime(Exchange exc) {
		if (exc.getTime() == null)
			return Constants.UNKNOWN;
		return DATE_FORMATTER.format(exc.getTime().getTime());
	}
	
	public static String getRequestContentLength(Exchange exc) {
		if (exc.getRequestContentLength() == -1)
			return Constants.UNKNOWN;
		return "" + exc.getRequestContentLength();
	}
	
	public static String getResponseContentLength(Exchange exc) {
		if (exc.getResponseContentLength() == -1)
			return Constants.UNKNOWN;
		return "" + exc.getResponseContentLength();
	}
	
	public static String getResponseContentType(Exchange exc) {
		if (exc.getResponse() == null)
			return Constants.N_A;
		return exc.getResponseContentType();
	}
	
	
	public static String getTimeDifference(Exchange exc) {
		return "" + (exc.getTimeResReceived() - exc.getTimeReqSent());
	}
}
