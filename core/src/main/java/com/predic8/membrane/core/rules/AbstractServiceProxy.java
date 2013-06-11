/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.rules;

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
		private boolean adjustHostHeader = true;
		
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
		
		public boolean isAdjustHostHeader() {
			return adjustHostHeader;
		}
		
		@MCAttribute
		public void setAdjustHostHeader(boolean adjustHostHeader) {
			this.adjustHostHeader = adjustHostHeader;
		}
	}
	
	protected Target target = new Target();
	private SSLParser sslInboundParser;
	private SSLContext sslInboundContext, sslOutboundContext;

	public String getTargetScheme() {
		return sslOutboundContext != null ? "https" : "http";
	}
	
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
	
	public String getTargetHost() {
		return target.getHost();
	}

	public int getTargetPort() {
		return target.getPort();
	}

	public String getTargetURL() {
		return target.getUrl();
	}
	
	public boolean isTargetAdjustHostHeader() {
		return target.isAdjustHostHeader();
	}

}
