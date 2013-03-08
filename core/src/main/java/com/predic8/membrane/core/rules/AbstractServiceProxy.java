package com.predic8.membrane.core.rules;

import static org.apache.commons.lang.StringUtils.defaultString;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.lang.StringUtils;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.transport.SSLContext;

public abstract class AbstractServiceProxy extends AbstractProxy {

	private String targetHost;
	private int targetPort;
	private String targetURL;
	private SSLParser sslInboundParser;
	private SSLContext sslInboundContext, sslOutboundContext;

	public String getTargetHost() {
		return targetHost;
	}

	public String getTargetScheme() {
		return sslOutboundContext != null ? "https" : "http";
	}

	public void setTargetHost(String targetHost) {
		this.targetHost = targetHost;
	}

	public int getTargetPort() {
		return targetPort;
	}

	public void setTargetPort(int targetPort) {
		this.targetPort = targetPort;
	}

	public String getTargetURL() {
		return targetURL;
	}

	public void setTargetURL(String targetURL) {
		this.targetURL = targetURL;
	}

	protected int parsePort(XMLStreamReader token) {
		return Integer.parseInt(defaultString(token.getAttributeValue("", "port"),"80"));
	}

	protected String parseHost(XMLStreamReader token) {
		return defaultString(token.getAttributeValue("", "host"), "*");
	}
	
	protected String parseIp(XMLStreamReader token) {
		return token.getAttributeValue("", "ip");
	}

	
	@Override
	public void write(XMLStreamWriter out)
			throws XMLStreamException {
		
		out.writeStartElement(getElementName());
		
		writeRule(out);
		
		writeTarget(out);
		out.writeEndElement();
	}
	
	protected abstract void writeTarget(XMLStreamWriter out) throws XMLStreamException;

	@Override
	public SSLContext getSslInboundContext() {
		return sslInboundContext;
	}
	
	protected void setSslInboundContext(SSLContext sslInboundContext) {
		this.sslInboundContext = sslInboundContext;
	}
	
	@Override
	public SSLContext getSslOutboundContext() {
		return sslOutboundContext;
	}
	
	protected void setSslOutboundContext(SSLContext sslOutboundContext) {
		this.sslOutboundContext = sslOutboundContext;
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {		
		if ("ssl".equals(child)) {
			sslInboundParser = new SSLParser();
			sslInboundParser.parse(token);
		} else {
			super.parseChildren(token, child);
		}
	}

	public void setSslInboundParser(SSLParser sslInboundParser) {
		this.sslInboundParser = sslInboundParser;
	}

	@Override
	public String getName() {
		return StringUtils.defaultIfEmpty(name, getKey().toString());
	}

	@Override
	public void init(Router router) throws Exception {
		super.init(router);
		if (sslInboundParser != null)
			setSslInboundContext(new SSLContext(sslInboundParser, router.getResourceResolver()));
	}

}
