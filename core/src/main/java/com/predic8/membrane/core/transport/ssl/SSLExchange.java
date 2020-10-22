/* Copyright 2020 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

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
