package com.predic8.membrane.core.interceptor.rest;

import java.io.StringReader;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.xml.Request;
import com.predic8.membrane.core.http.xml.URI;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
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

		replaceRequestBody(exc, regex);
		updateRequest(exc, regex);

		return Outcome.CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		log.debug("restURL: " + getRESTURL(exc));

		transform(exc.getResponse(),
				getResponseXSLT(getRESTURL(exc)),
				new StreamSource(exc.getResponse().getBodyAsStream()));
		return Outcome.CONTINUE;
	}

	private String getRESTURL(Exchange exc) {
		return (String) exc.getProperty("restURL");
	}

	private void replaceRequestBody(AbstractExchange exc, String regex)
			throws Exception {
		Request req = new Request();

		req.setMethod(exc.getRequest().getMethod());
		req.setHttpVersion(exc.getRequest().getVersion());

		URI uri = new URI();
		uri.setValue(getURI(exc));
		req.setUri(uri);

		String res = req.toXml();
		log.debug("http-xml: " + res);
		transform(exc.getRequest(), getRequestXSLT(regex), 
				  new StreamSource(new StringReader(res)));
	}

	private String findFirstMatchingRegEx(String uri) {
		for (String regex : mappings.keySet()) {
			if (Pattern.matches(regex, uri))
				return regex;
		}
		return null;
	}

	private void updateRequest(AbstractExchange exc, String regex) {

		exc.getRequest().setMethod("POST");
		exc.getRequest().getHeader().add("SOAPAction", getSOAPAction(regex));
		exc.setProperty("restURL", regex);
		replaceURI(exc, regex);
	}

	private void replaceURI(AbstractExchange exc, String regex) {
		exc.getRequest().setUri(
				getURI(exc).replaceAll(regex, getSOAPURL(regex)));
	}

	private void transform(Message msg, String ss, Source src) throws Exception {
		msg.setBodyContent(xsltTransformer.transform(ss, src).getBytes("UTF-8"));
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

	private String getSOAPURL(String regex) {
		return mappings.get(regex).get("SOAPURL");
	}

	public Map<String, Map<String, String>> getMappings() {
		return mappings;
	}

	public void setMappings(Map<String, Map<String, String>> mappings) {
		this.mappings = mappings;
	}

}
