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

package com.predic8.membrane.core.proxies;

import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.util.regex.*;

import static java.util.regex.Pattern.*;

/**
 * Adds HTTP specific stuff like Method
 */
public class ServiceProxyKey extends AbstractRuleKey {
    private static final Logger log = LoggerFactory.getLogger(ServiceProxyKey.class.getName());

    private String method = "*";
    private String host = "*";
    private boolean isHostWildCard = true;
    private Pattern hostPattern;

    public ServiceProxyKey(RuleKey key) {
        super(key);
        method = key.getMethod();
        host = key.getHost();

        if (key instanceof AbstractRuleKey arKey) {
            isHostWildCard = arKey.isHostWildcard();
        }

        if (key instanceof ServiceProxyKey spKey) {
            hostPattern = spKey.getHostPattern();
        }
    }

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

    @Override
    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    @Override
    public boolean isMethodWildcard() {
        return "*".equals(method.trim());
    }

    @Override
    public boolean isHostWildcard() {
        return isHostWildCard;
    }

    @Override
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host.trim();
        isHostWildCard = "*".equals(this.host);
        if (!isHostWildCard) {
            String pattern = createHostPattern(this.host);
            log.debug("Created host pattern match: {}", pattern);
            this.hostPattern = Pattern.compile(pattern, CASE_INSENSITIVE);
        } else {
            this.hostPattern = null;
        }
    }

	public static String createHostPattern(String host) {
		StringBuilder regex = new StringBuilder();
		boolean quoted = false;
		boolean notWhitespace = false;
		regex.append("(");
		for (char c : host.toCharArray()) {
			switch (c) {
				case ' ':
					if (!notWhitespace) {
						continue;
					}
					if (quoted) {
						regex.append("\\E");
						quoted = false;
					}
					notWhitespace = false;
					regex.append(")|(");
					break;
				case '*':
					if (quoted) {
						regex.append("\\E");
						quoted = false;
					}
					regex.append(".+");
					notWhitespace = true;
					break;
				default:
					if (!quoted) {
						regex.append("\\Q");
						quoted = true;
						notWhitespace = true;
					}
					if (c == '\\')
						regex.append('\\');
					regex.append(c);
					break;
			}
		}
		if (quoted) {
			regex.append("\\E");
		}
		if (!notWhitespace && regex.length() > 1) {
			regex.setLength(regex.length() - 3);
		}
		regex.append(")");
		return regex.toString();
	}


    @Override
    public boolean matchesHostHeader(String hostHeader) {
        if (isHostWildCard)
            return true;

        if (hostHeader == null)
            return false;

        String requestHost = hostHeader.split(":")[0];

        log.debug("Rule host: {} Request host: {}", host, requestHost);

        return hostPattern.matcher(requestHost).matches();
    }

    /**
     * The pattern used to match the host name, or null if any host name matches.
     */
    public Pattern getHostPattern() {
        return hostPattern;
    }

    @Override
    public String toString() {
        return getHostString() + ":" + port + getMethodString() + getPathString();
    }

    private String getPathString() {
        if (getPath() != null && !getPath().isEmpty()) {
            return " " + getPath();
        }
        return "";
    }

    private @NotNull String getMethodString() {
        return method.equals("*") ? "" : " %s".formatted(method);
    }

    private String getHostString() {
        return host.equals("*") ? "0.0.0.0" : host;
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
            return other.getPath() == null;
        } else return getPath().equals(other.getPath());
    }
}
