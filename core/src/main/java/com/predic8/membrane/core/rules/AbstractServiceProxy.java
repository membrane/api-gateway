package com.predic8.membrane.core.rules;

import static org.apache.commons.lang.StringUtils.defaultString;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.lang.StringUtils;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.transport.SSLContext;

public abstract class AbstractServiceProxy extends AbstractProxy {

	@MCElement(name="target", group="util", global=false)
	public static class Target {
		private String host;
		private int port = 80;
		private String url;
		
		private SSLParser sslParser;
		
		public String getHost() {
			return host;
		}
		
		@MCAttribute
		public void setHost(String host) {
			this.host = host;
		}
		
		public int getPort() {
			return port;
		}
		
		@MCAttribute
		public void setPort(int port) {
			this.port = port;
		}
		
		public String getUrl() {
			return url;
		}
		
		@MCAttribute
		public void setUrl(String url) {
			this.url = url;
		}
		
		public SSLParser getSslParser() {
			return sslParser;
		}
		
		@MCChildElement
		public void setSslParser(SSLParser sslParser) {
			this.sslParser = sslParser;
		}
	}
	
	protected Target target = new Target();
	private SSLParser sslInboundParser;
	private SSLContext sslInboundContext, sslOutboundContext;

	public String getTargetScheme() {
		return sslOutboundContext != null ? "https" : "http";
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

	public SSLParser getSslInboundParser() {
		return sslInboundParser;
	}

	@MCChildElement(order=75)
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
		if (target.getSslParser() != null)
			setSslOutboundContext(new SSLContext(target.getSslParser(), router.getResourceResolver()));
	}
	
	public int getPort() {
		return ((ServiceProxyKey)key).getPort();
	}

	@MCAttribute
	public void setPort(int port) {
		((ServiceProxyKey)key).setPort(port);
	}
	
	public String getIp() {
		return ((ServiceProxyKey)key).getIp();
	}
	
	@MCAttribute
	public void setIp(String ip) {
		((ServiceProxyKey)key).setIp(ip);
	}
	
	public String getHost() {
		return ((ServiceProxyKey)key).getHost();
	}
	
	@MCAttribute
	public void setHost(String host) {
		((ServiceProxyKey)key).setHost(host);
	}
	
	public Path getPath() {
		ServiceProxyKey k = (ServiceProxyKey)key;
		if (!k.isUsePathPattern())
			return null;
		return new Path(k.isPathRegExp(), k.getPath());
	}

	@MCChildElement(order=50)
	public void setPath(Path path) {
		ServiceProxyKey k = (ServiceProxyKey)key;
		k.setUsePathPattern(path != null);
		if (path != null) {
			k.setPathRegExp(path.isRegExp());
			k.setPath(path.getValue());
		}
	}
	
}
