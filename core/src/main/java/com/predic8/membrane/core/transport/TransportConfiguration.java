/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.util.ConfigurationException;

import static java.lang.Integer.MAX_VALUE;

/**
 * @description <p>Settings for incoming connections. It is the inbound counterpart of <code>httpClientConfig</code>,
 * which configures outgoing connections.</p>
 * <p>Changes take effect when the configuration is reloaded, because all server ports are reopened.</p>
 * @yaml <pre><code>
 * configuration:
 *   transport:
 *     backlog: 1024
 *     socketTimeout: 30000
 *     maxThreadPoolSize: 300
 *     concurrentConnectionLimitPerIp: 100
 * </code></pre>
 */
@MCElement(name = "transport", component = false, id = "transport-configuration")
public class TransportConfiguration {

    private int backlog = 500;
    private int socketTimeout = 30000;
    private boolean tcpNoDelay = true;
    private int forceSocketCloseOnHotDeployAfter = 30000;
    private int coreThreadPoolSize = 20;
    private int maxThreadPoolSize = MAX_VALUE;
    private boolean reverseDNS = true;
    private int concurrentConnectionLimitPerIp = -1;

    /**
     * Checks the settings that depend on each other. Called while the configuration is parsed, so that an invalid
     * configuration is rejected before a running router is shut down for a reload.
     */
    public void validate() {
        if (coreThreadPoolSize < 0)
            throw new ConfigurationException("coreThreadPoolSize must not be negative, but was %d.".formatted(coreThreadPoolSize));
        if (maxThreadPoolSize < 1)
            throw new ConfigurationException("maxThreadPoolSize must be at least 1, but was %d.".formatted(maxThreadPoolSize));
        if (coreThreadPoolSize > maxThreadPoolSize)
            throw new ConfigurationException("coreThreadPoolSize (%d) must not be greater than maxThreadPoolSize (%d). coreThreadPoolSize defaults to 20."
                    .formatted(coreThreadPoolSize, maxThreadPoolSize));
    }

    public int getBacklog() {
        return backlog;
    }

    /**
     * @description <p>Maximum length of the queue of incoming connections that have not been accepted yet. When the
     * queue is full, further connection attempts are dropped (Linux, macOS) or refused (Windows).</p>
     * <p>The operating system silently caps the value:</p>
     * <ul>
     * <li>Linux: <code>net.core.somaxconn</code>, 4096 by default since kernel 5.4, 128 before.
     * Raise it with <code>sysctl -w net.core.somaxconn=...</code>.</li>
     * <li>macOS: <code>kern.ipc.somaxconn</code>, 128 by default.</li>
     * <li>Windows: 200.</li>
     * <li>Containers: <code>net.core.somaxconn</code> is set per network namespace, e.g. with
     * <code>docker run --sysctl net.core.somaxconn=...</code> or a Kubernetes pod's <code>securityContext.sysctls</code>.</li>
     * </ul>
     * <p>A value of 0 or less uses the Java default of 50.</p>
     * @default 500
     * @example 1024
     */
    @MCAttribute
    public void setBacklog(int backlog) {
        this.backlog = backlog;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    /**
     * @description Read timeout for client connections in milliseconds.
     * @default 30000
     */
    @MCAttribute
    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    /**
     * @description Whether to set the TCP_NODELAY socket option, which sends data immediately instead of waiting
     * briefly for more data to fill a packet (Nagle's algorithm).
     * @default true
     */
    @MCAttribute
    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public int getForceSocketCloseOnHotDeployAfter() {
        return forceSocketCloseOnHotDeployAfter;
    }

    /**
     * @description On a reload, connections that are still busy are closed forcibly after this many milliseconds.
     * @default 30000
     */
    @MCAttribute
    public void setForceSocketCloseOnHotDeployAfter(int forceSocketCloseOnHotDeployAfter) {
        this.forceSocketCloseOnHotDeployAfter = forceSocketCloseOnHotDeployAfter;
    }

    public int getCoreThreadPoolSize() {
        return coreThreadPoolSize;
    }

    /**
     * @description Number of threads kept ready to serve client connections. Must not be greater than
     * <code>maxThreadPoolSize</code>.
     * @default 20
     */
    @MCAttribute
    public void setCoreThreadPoolSize(int coreThreadPoolSize) {
        this.coreThreadPoolSize = coreThreadPoolSize;
    }

    public int getMaxThreadPoolSize() {
        return maxThreadPoolSize;
    }

    /**
     * @description Maximum number of threads that serve client connections. Membrane uses one thread per connection.
     * @default no limit
     * @example 300
     */
    @MCAttribute
    public void setMaxThreadPoolSize(int maxThreadPoolSize) {
        this.maxThreadPoolSize = maxThreadPoolSize;
    }

    public boolean isReverseDNS() {
        return reverseDNS;
    }

    /**
     * @description Whether to look up the host name of the client's IP address.
     * @default true
     */
    @MCAttribute
    public void setReverseDNS(boolean reverseDNS) {
        this.reverseDNS = reverseDNS;
    }

    public int getConcurrentConnectionLimitPerIp() {
        return concurrentConnectionLimitPerIp;
    }

    /**
     * @description Maximum number of concurrent connections from one client IP address. -1 means no limit.
     * @default -1
     */
    @MCAttribute
    public void setConcurrentConnectionLimitPerIp(int concurrentConnectionLimitPerIp) {
        this.concurrentConnectionLimitPerIp = concurrentConnectionLimitPerIp;
    }
}
