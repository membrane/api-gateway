package com.predic8.membrane.core.interceptor.rest;

import java.io.StringReader;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.http.xml.Request;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.xslt.XSLTTransformer;

public class REST2SOAPInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(REST2SOAPInterceptor.class.getName());

	private Map<String, Map<String, String>> mappings;
	private XSLTTransformer xsltTransformer = new XSLTTransformer();

	public REST2SOAPInterceptor() {
		priority = 140;
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		log.debug("uri: " + getURI(exc));
		String uri = getURI(exc);

		String regex = findFirstMatchingRegEx(uri);
		if (regex == null)
			return Outcome.CONTINUE;

		transformAndReplaceBody(exc.getRequest(), 
								getRequestXSLT(regex), 
								getRequestXMLSource(exc));
		modifyRequest(exc, regex);

		return Outcome.CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		log.debug("restURL: " + getRESTURL(exc));
		if (getRESTURL(exc)==null) return Outcome.CONTINUE;

		log.debug("response: " + xsltTransformer.transform(null, getBodySource(exc)));		
		
		transformAndReplaceBody(exc.getResponse(),
				                getResponseXSLT(getRESTURL(exc)),
				                getBodySource(exc));
		setContentType(exc.getResponse().getHeader());
		return Outcome.CONTINUE;
	}

	private StreamSource getRequestXMLSource(Exchange exc) throws Exception {
		Request req = new Request(exc.getRequest());
		
		String res = req.toXml();
		log.debug("http-xml: " + res);		
		
		return new StreamSource(new StringReader(res));
	}

	private StreamSource getBodySource(Exchange exc) {
		return new StreamSource(exc.getResponse().getBodyAsStream());
	}

	private String getRESTURL(Exchange exc) {
		return (String) exc.getProperty("restURL");
	}

	private String findFirstMatchingRegEx(String uri) {
		for (String regex : mappings.keySet()) {
			if (Pattern.matches(regex, uri))
				return regex;
		}
		return null;
	}

	private void modifyRequest(AbstractExchange exc, String regex) {

		exc.getRequest().setMethod("POST");
		exc.getRequest().getHeader().add("SOAPAction", getSOAPAction(regex));
		setContentType(exc.getRequest().getHeader());
		
		exc.setProperty("restURL", regex);
		setServiceEndpoint(exc, regex);
	}

	private void setContentType(Header header) {
		header.removeFields("Content-Type");
		header.add("Content-Type","text/xml;charset=UTF-8");
	}

	private void setServiceEndpoint(AbstractExchange exc, String regex) {
		exc.getRequest().setUri(
				getURI(exc).replaceAll(regex, getSOAPURI(regex)));
	}

	private void transformAndReplaceBody(Message msg, String ss, Source src) throws Exception {
		String soapEnv = xsltTransformer.transform(ss, src);
		log.debug("soap-env: " + soapEnv);
		msg.setBodyContent(soapEnv.getBytes("UTF-8"));
	}

	private String getURI(AbstractExchange exc) {
		return exc.getRequest().getUri();
	}

	private String getResponseXSLT(String regex) {
		return mappings.get(regex).get("responseXSLT");
	}

	private String getRequestXSLT(String regex) {
		return mappings.get(regex).get("requestXSLT");
	}

	private String getSOAPAction(String regex) {
		return mappings.get(regex).get("SOAPAction");
	}

	private String getSOAPURI(String regex) {
		return mappings.get(regex).get("SOAPURI");
	}

	public Map<String, Map<String, String>> getMappings() {
		return mappings;
	}

	public void setMappings(Map<String, Map<String, String>> mappings) {
		this.mappings = mappings;
	}

}
