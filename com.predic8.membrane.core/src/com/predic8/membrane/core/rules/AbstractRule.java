package com.predic8.membrane.core.rules;

import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.config.AbstractConfigElement;
import com.predic8.membrane.core.config.Interceptors;
import com.predic8.membrane.core.config.LocalHost;
import com.predic8.membrane.core.interceptor.Interceptor;

public abstract class AbstractRule extends AbstractConfigElement implements Rule {

	protected String name = "";
	
	protected RuleKey key;
	
	protected boolean blockRequest;
	protected boolean blockResponse;
	
	protected boolean inboundTLS;
	
	protected boolean outboundTLS;
	
	protected List<Interceptor> interceptors = new ArrayList<Interceptor>();
	
	/**
	 * Used to determine the IP address for outgoing connections
	 */
	protected String localHost;
	
	public AbstractRule() {
		super(null);
	}
	
	public AbstractRule(RuleKey ruleKey) {
		super(null);
		this.key = ruleKey;
	}
	
	public List<Interceptor> getInterceptors() {
		return interceptors;
	}

	public void setInterceptors(List<Interceptor> interceptors) {
		this.interceptors = interceptors;
	}

	public String getName() {
		return name;
	}

	public RuleKey getKey() {
		return key;
	}

	public boolean isBlockRequest() {
		return blockRequest;
	}

	public boolean isBlockResponse() {
		return blockResponse;
	}

	public void setName(String name) {
		if (name == null)
			return;
		this.name = name;

	}

	public void setKey(RuleKey ruleKey) {
		this.key = ruleKey;
	}
	
	@Override
	public String toString() {
		if (!"".equals(name))
			return name;
		return "" + getKey().toString();
	}
	
	public void setBlockRequest(boolean blockStatus) {
		this.blockRequest = blockStatus;
	}
	
	public void setBlockResponse(boolean blockStatus) {
		this.blockResponse = blockStatus;
	}

	public boolean isInboundTLS() {
		return inboundTLS;
	}
	
	public boolean isOutboundTLS() {
		return outboundTLS;
	}
	
	public void setInboundTLS(boolean status) {
		inboundTLS = status;	
	}
	
	public void setOutboundTLS(boolean status) {
		this.outboundTLS = status;
	}

	public String getLocalHost() {
		return localHost;
	}

	public void setLocalHost(String localHost) {
		this.localHost = localHost;
	}
	
	protected void writeLocalHost(XMLStreamWriter out) throws XMLStreamException {
		if (localHost == null)
			return;
		
		new LocalHost(localHost).write(out);
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (LocalHost.ELEMENT_NAME.equals(child)) {
			this.localHost = ((LocalHost) (new LocalHost().parse(token))).getValue();
		} else if (Interceptors.ELEMENT_NAME.equals(child)) {
			Interceptors interceptorsElement = new Interceptors(router);
			this.interceptors = ((Interceptors) (interceptorsElement.parse(token))).getInterceptors();
		}  
	}
	
	protected void writeLeading(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(getElementName());
		out.writeAttribute("name", name);
		out.writeAttribute("port", "" + key.getPort());
		out.writeAttribute("blockRequest", "" + Boolean.toString(blockRequest));
		out.writeAttribute("blockResponse", "" + Boolean.toString(blockResponse));
		writeTls(out);
	}
	
	protected void writeTrailing(XMLStreamWriter out) throws XMLStreamException {
		writeLocalHost(out);
		writeInterceptors(out);
		out.writeEndElement();
	}
	
	protected void writeInterceptors(XMLStreamWriter out) throws XMLStreamException {
		Interceptors inters = new Interceptors(router);
		inters.setInterceptors(interceptors);
		inters.write(out);
	}
	
	protected void writeTls(XMLStreamWriter out) throws XMLStreamException {
		out.writeAttribute("inboundTLS", Boolean.toString(inboundTLS));
		out.writeAttribute("outboundTLS", Boolean.toString(outboundTLS));
	}
	
	protected void parseTLS(XMLStreamReader token) {
		inboundTLS = getBoolean(token, "inboundTLS");
		outboundTLS = getBoolean(token, "outboundTLS");
	}
	
	protected void parseBlocking(XMLStreamReader token) {
		blockRequest = getBoolean(token, "blockRequest");
		blockResponse = getBoolean(token, "blockResponse");
	}
	
	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		writeLeading(out);
		writeExtension(out);
		writeTrailing(out);
	}
	
	protected abstract void parseKeyAttributes(XMLStreamReader token);
	
	@Override
	protected void parseAttributes(XMLStreamReader token) {
		name = token.getAttributeValue("", "name");
		parseKeyAttributes(token);
		parseTLS(token);
		parseBlocking(token);
	}
	
	protected void writeExtension(XMLStreamWriter out)
			throws XMLStreamException {
	}
	
}
