package com.predic8.membrane.core.transport.ssl;

import com.predic8.membrane.core.rules.Rule;

import java.util.HashMap;
import java.util.Map;

public class SSLExchange {
    protected Rule rule;
    protected String remoteAddrIp;
    protected Map<String, Object> properties = new HashMap<String, Object>();
    protected TLSError error;
    private int remotePort;

    public Rule getRule() {
        return rule;
    }

    public void setRule(Rule rule) {
        this.rule = rule;
    }

    public String getRemoteAddrIp() {
        return remoteAddrIp;
    }

    public void setRemoteAddrIp(String remoteAddrIp) {
        this.remoteAddrIp = remoteAddrIp;
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    public void setError(TLSError error) {
        this.error = error;
    }

    public TLSError getError() {
        return error;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }
}
