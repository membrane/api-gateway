/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.util;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.XMemcachedClientBuilder;
import net.rubyeye.xmemcached.auth.AuthInfo;
import net.rubyeye.xmemcached.command.BinaryCommandFactory;
import net.rubyeye.xmemcached.utils.AddrUtil;
import org.springframework.beans.factory.InitializingBean;

@MCElement(name = "memcached")
public class MemcachedConnector implements InitializingBean {

    private String host = "localhost";
    private int port = 11211;
    private String username;
    private String password;

    private MemcachedClient client;
    private long connectTimeout = MemcachedClient.DEFAULT_CONNECT_TIMEOUT;

    @Override
    public void afterPropertiesSet() throws Exception {
        var servers = AddrUtil.getAddresses(host + ":" + port);

        var builder = new XMemcachedClientBuilder(servers);
        if (username != null || password != null) {
            var authInfo = AuthInfo.plain(username, password);
            servers.forEach(server -> builder.addAuthInfo(server, authInfo));
        }
        builder.setCommandFactory(new BinaryCommandFactory());
        builder.setConnectTimeout(connectTimeout);

        this.client = builder.build();
    }

    public MemcachedClient getClient() {
        return client;
    }

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

    public String getUsername() {
        return username;
    }

    @MCAttribute
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    @MCAttribute
    public void setPassword(String password) {
        this.password = password;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    @MCAttribute
    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
}
