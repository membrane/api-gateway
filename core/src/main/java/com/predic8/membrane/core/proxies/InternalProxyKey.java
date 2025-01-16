/* Copyright 2025 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.exchange.*;
import org.apache.commons.lang3.*;

public class InternalProxyKey implements RuleKey {

    private String serviceName;

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public int getPort() {
        return 0;
    }

    @Override
    public String getMethod() {
        return "";
    }

    @Override
    public String getPath() {
        return "";
    }

    @Override
    public String getHost() {
        return "";
    }

    @Override
    public boolean isMethodWildcard() {
        return true;
    }

    @Override
    public boolean isPathRegExp() {
        return false;
    }

    @Override
    public boolean isUsePathPattern() {
        return false;
    }

    @Override
    public void setUsePathPattern(boolean usePathPattern) {

    }

    @Override
    public void setPathRegExp(boolean pathRegExp) {

    }

    @Override
    public void setPath(String path) {

    }

    @Override
    public boolean matchesPath(String path) {
        return true;
    }

    @Override
    public String getIp() {
        return null;
    }

    @Override
    public void setIp(String ip) {

    }

    @Override
    public boolean matchesHostHeader(String hostHeader) {
        return true;
    }

    @Override
    public boolean matchesVersion(String version) {
        return true;
    }

    @Override
    public boolean complexMatch(Exchange exc) {
        return true;
    }

    @Override
    public String toString() {
        return StringUtils.defaultIfEmpty(serviceName, "");
    }
}
