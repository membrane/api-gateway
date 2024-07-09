/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.exchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractRuleKey implements RuleKey {

    private static Logger log = LoggerFactory.getLogger(AbstractRuleKey.class.getName());

    /**
     * -1 is used as a wildcard. It is used by HttpServletHandler, since its port
     * is determined by the webserver and not by the proxies.xml
     */
    protected int port;

    private String path;

    protected volatile Pattern pathPattern;

    protected boolean pathRegExp = true;

    protected boolean usePathPattern;

    protected String ip;

    public AbstractRuleKey(int port, String ip) {
        this.port = port;
        this.ip = ip;
    }

    public String getHost() {
        return "";
    }

    public String getMethod() {
        return "";
    }

    public int getPort() {
        return port;
    }

    public boolean isHostWildcard() {

        return false;
    }

    public boolean isMethodWildcard() {
        return false;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isPathRegExp() {
        return pathRegExp;
    }

    public void setPathRegExp(boolean pathRegExp) {
        this.pathRegExp = pathRegExp;
    }

    public boolean isUsePathPattern() {
        return usePathPattern;
    }

    public void setUsePathPattern(boolean usePathPattern) {
        this.usePathPattern = usePathPattern;
        pathPattern = null;
    }

    public void setPath(String path) {
        this.path = path;
        pathPattern = null;
    }

    public String getPath() {
        return path;
    }

    public boolean matchesPath(String path) {
        if (isPathRegExp())
            return matchesPathPattern(path);
        return path.startsWith(getPath());
    }

    private boolean matchesPathPattern(String path) {
        log.debug("matches path: " + path + " with path pattern: "
                  + getPathPattern());
        return getPathPattern().matcher(path).matches();
    }

    private Pattern getPathPattern() {
        if (pathPattern != null)
            return pathPattern;

        synchronized (this) {
            // Check again this time in synchronized block
            if (pathPattern != null)
                return pathPattern;
            pathPattern = Pattern.compile(computePathPattern(path));
        }

        return pathPattern;
    }

    private String computePathPattern(String path) {
        return path != null ? path : ".*";
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    @Override
    public boolean matchesHostHeader(String hostHeader) {
        return false;
    }

    @Override
    public boolean matchesVersion(String version) {
        return !"STOMP".equals(version);
    }

    public boolean complexMatch(Exchange exc) {
        return true;
    }
}
