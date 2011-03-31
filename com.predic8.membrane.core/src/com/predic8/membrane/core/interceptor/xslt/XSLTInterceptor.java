package com.predic8.membrane.core.interceptor.xslt;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;

import javax.swing.text.html.HTMLDocument.RunElement;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class XSLTInterceptor extends AbstractInterceptor {

	private String requestXSLT;
	private String responseXSLT;
	private TransformerFactory fac = TransformerFactory.newInstance();
	
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
		if (!hasStylesheet(ss)) return;
		
		msg.setBodyContent(applyStylesheet(msg.getBodyAsStream(), getFile(ss)).getBytes("UTF-8"));
	}

	private boolean hasStylesheet(String ss) {
		return ss!=null && !"".equals(ss);
	}

	private String applyStylesheet(InputStream input, File ss)
			throws Exception {
		Transformer t = fac.newTransformer(new StreamSource(ss));
		StringWriter sw = new StringWriter();
		t.transform(new StreamSource(input),
					new StreamResult(sw));
		return sw.toString();
	}
	
	private File getFile(String path) {
		if ( new File(path).isAbsolute() )
			return new File(path);
		
		return new File(System.getenv("MEMBRANE_HOME"), path);
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
