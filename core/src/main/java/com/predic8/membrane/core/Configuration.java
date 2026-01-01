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

package com.predic8.membrane.core;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.interceptor.administration.AdminConsoleInterceptor;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.util.URIFactory;

@MCElement(name = "configuration", topLevel = true, component = false)
public class Configuration {

    /**
     * Set production to true to run Membrane in production mode.
     * In production mode the security level is increased e.g. there is less information
     * in error messages sent to clients.
     */
    private boolean production;

    private boolean hotDeploy = true;

    private int retryInitInterval = 5 * 60 * 1000; // 5 minutes

    private boolean retryInit = false;

    private String jmxRouterName = "default";

    private URIFactory uriFactory = new URIFactory(false);

    /**
     * @param hotDeploy If true the hot deploy feature will be activated during init of the Router.
     * @description <p>Whether changes to the router's configuration file should automatically trigger a restart.
     * </p>
     * <p>
     * Monitoring the router's configuration file <i>proxies.xml</i> is only possible, if the router
     * is created by a Spring Application Context which supports monitoring.
     * </p>
     * <p>Calling this method does not start or stop the hot deploy feature. It is just for configuration before init is called.</p>
     * @default true
     */
    @MCAttribute
    public void setHotDeploy(boolean hotDeploy) {
        this.hotDeploy = hotDeploy;
    }

    public boolean isHotDeploy() {
        return hotDeploy;
    }

    /**
     * @description number of milliseconds after which reinitialization of &lt;soapProxy&gt;s should be attempted periodically
     * @default 5 minutes
     */
    @MCAttribute
    public void setRetryInitInterval(int retryInitInterval) {
        this.retryInitInterval = retryInitInterval;
    }

    public int getRetryInitInterval() {
        return retryInitInterval;
    }

    /**
     * @description  <p>Whether the router should continue startup, if initialization of a rule (proxy, serviceProxy or soapProxy) failed
     * (for example, when a WSDL a component depends on could not be downloaded).</p>
     * <p>If false, the router will exit with code -1 just after startup, when the initialization of a rule failed.</p>
     * <p>If true, the router will continue startup, and all rules which could not be initialized will be <i>inactive</i> (=not
     * {@link Proxy#isActive()}).</p>
     * <h3>Inactive rules</h3>
     * <p>Inactive rules will simply be ignored for routing decisions for incoming requests.
     * This means that requests for inactive rules might be routed using different routes or result in a "400 Bad Request"
     * when no active route could be matched to the request.</p>
     * <p>Once rules become active due to reinitialization, they are considered in future routing decision.</p>
     * <h3>Reinitialization</h3>
     * <p>Inactive rules may be <i>reinitialized</i> and, if reinitialization succeeds, become active.</p>
     * <p>By default, reinitialization is attempted at regular intervals using a timer (see {@link #setRetryInitInterval(int)}).</p>
     * <p>Additionally, using the {@link AdminConsoleInterceptor}, an admin may trigger reinitialization of inactive rules at any time.</p>
     * @default false
     */
    @MCAttribute
    public void setRetryInit(boolean retryInit) {
        this.retryInit = retryInit;
    }

    public boolean isRetryInit() {
        return retryInit;
    }

    /**
     * @description Sets the JMX name for this router. Also declare a global &lt;jmxExporter&gt; instance.
     */
    @MCAttribute
    public void setJmxRouterName(String jmxRouterName) {
        this.jmxRouterName = jmxRouterName;
    }

    public String getJmx() {
        return jmxRouterName;
    }

    /**
     * @description  <p>By default the error messages Membrane sends back to an HTTP client provide information to help the caller
     * find the problem. The caller might even get sensitive information. In production the error messages should not reveal
     * to much details. With this option you can put Membrane in production mode and reduce the amount of information in
     * error messages.</p>
     * @default false
     */
    @MCAttribute
    public void setProduction(boolean production) {
        this.production = production;
    }

    public boolean isProduction() {
        return production;
    }

    /**
     * @description Sets the URI factory used by the router. Use this only, if you need to allow
     * special (off-spec) characters in URLs which are not supported by java.net.URI .
     */
    @MCChildElement(order = -1, allowForeign = true)
    public void setUriFactory(URIFactory uriFactory) {
        this.uriFactory = uriFactory;
    }

    public URIFactory getUriFactory() {
        return uriFactory;
    }
}
