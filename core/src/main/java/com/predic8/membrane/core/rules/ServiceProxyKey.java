/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

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

import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ServiceProxyKey extends AbstractRuleKey {
	private static Log log = LogFactory.getLog(ServiceProxyKey.class.getName());

	private String method = "*";
	private String host = "*";
	private boolean isHostWildCard = true;
	private Pattern hostPattern;

	public ServiceProxyKey(int port) {
		this(port, null);
	}

	public ServiceProxyKey(int port, String ip) {
		super(port, ip);
	}
	
	public ServiceProxyKey(String host, String method, String path, int port) {
		this(host, method, path, port, null);
	}

	public ServiceProxyKey(String host, String method, String path, int port, String ip) {
		super(port, ip);
		setHost(host);
		setPath(path);
		this.method = method;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public boolean isMethodWildcard() {
		return "*".equals(method.trim());
	}

	public boolean isHostWildcard() {
		return isHostWildCard;
	}

	public String toString() {
		return host + " " + method + " " + getPath() + ":" + port;
	}

	

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((host == null) ? 0 : host.hashCode());
		result = prime * result + ((method == null) ? 0 : method.hashCode());
		result = prime * result + ((getPath() == null) ? 0 : getPath().hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		ServiceProxyKey other = (ServiceProxyKey) obj;
		if (host == null) {
			if (other.host != null)
				return false;
		} else if (!host.equals(other.host))
			return false;
		if (method == null) {
			if (other.method != null)
				return false;
		} else if (!method.equals(other.method))
			return false;
		if (getPath() == null) {
			if (other.getPath() != null)
				return false;
		} else if (!getPath().equals(other.getPath()))
			return false;
		return true;
	}

	public String getHost() {
		return host;
	}
	
	public void setHost(String host) {
		this.host = host.trim();
		this.isHostWildCard = "*".equals(this.host);
		if (!isHostWildCard) {
			String pattern = createHostPattern(this.host);
			log.debug("Created host pattern match: " + pattern);
			this.hostPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
		} else {
			this.hostPattern = null;
		}
	}

	public static String createHostPattern(String host) {
		StringBuilder regex = new StringBuilder();
		boolean quoted = false;
		boolean started = false;
		regex.append("(");
		for (int i = 0; i < host.length(); i++) {
			char c = host.charAt(i);
			switch (c) {
			case ' ':
				if (!started)
					break;
				if (quoted) {
					regex.append("\\E");
					quoted = false;
				}
				started = false;
				regex.append(")|(");
				break;
			case '*':
				if (quoted) {
					regex.append("\\E");
					quoted = false;
				}
				regex.append(".+");
				started = true;
				break;
			default:
				if (!quoted) {
					regex.append("\\Q");
					quoted = true;
					started = true;
				}
				if (c == '\\')
						regex.append('\\');
				regex.append(c);
			}
		}
		if (quoted) {
			regex.append("\\E");
			quoted = false;
		}
		if (!started && regex.length() > 1) {
			regex.delete(regex.length()-3, regex.length());
		}
		regex.append(")");
		
		String r = regex.toString();
		
		return r;
	}

	@Override
	public boolean matchesHostHeader(String hostHeader) {
		if (isHostWildCard)
			return true;

		if (hostHeader == null)
			return false;
		
		String requestHost = hostHeader.split(":")[0];

		log.debug("Rule host: " + host + ";  Request host: " + requestHost);
			
		return hostPattern.matcher(requestHost).matches();
	}
	
	/**
	 * The pattern used to match the host name, or null if any host name matches.
	 */
	public Pattern getHostPattern() {
		return hostPattern;
	}
}
