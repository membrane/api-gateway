/* Copyright 2018 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exchange.snapshots;

import com.predic8.membrane.core.rules.RuleKey;

public class FakeKey implements RuleKey {

    int port;

    public FakeKey(int port) {
        this.port = port;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getMethod() {
        return null;
    }

    @Override
    public String getPath() {
        return null;
    }

    @Override
    public String getHost() {
        return null;
    }

    @Override
    public boolean isMethodWildcard() {
        return false;
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
        return false;
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
        return false;
    }

    @Override
    public boolean matchesVersion(String version) {
        return false;
    }

    @Override
    public boolean complexMatch(String hostHeader, String method, String uri, String version, int port, String localIP) {
        return false;
    }
}
