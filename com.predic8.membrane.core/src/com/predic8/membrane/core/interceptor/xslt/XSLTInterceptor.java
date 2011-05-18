package com.predic8.membrane.core.interceptor.xslt;

import java.io.InputStream;

import javax.xml.transform.stream.StreamSource;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.rest.REST2SOAPInterceptor;

public class XSLTInterceptor extends AbstractInterceptor {	
	
	private String requestXSLT;
	private String responseXSLT;
	private XSLTTransformer xsltTransformer = new XSLTTransformer();
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		transformMsg(exc.getRequest(), requestXSLT);
		return Outcome.CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		transformMsg(exc.getResponse(), responseXSLT);
		return Outcome.CONTINUE;
	}

	private void transformMsg(Message msg, String ss) throws Exception {
		if ( msg.isBodyEmpty() ) return;
		msg.setBodyContent(xsltTransformer.transform(ss, new StreamSource(msg.getBodyAsStream())).getBytes("UTF-8"));
	}

	public String getRequestXSLT() {
		return requestXSLT;
	}

	public void setRequestXSLT(String requestXSLT) {
		this.requestXSLT = requestXSLT;
	}

	public String getResponseXSLT() {
		return responseXSLT;
	}

	public void setResponseXSLT(String responseXSLT) {
		this.responseXSLT = responseXSLT;
	}

}
