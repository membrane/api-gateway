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
