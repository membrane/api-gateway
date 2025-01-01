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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.stats.*;
import com.predic8.membrane.core.transport.ssl.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.util.*;

public abstract class AbstractProxy implements Rule {
    private static final Logger log = LoggerFactory.getLogger(AbstractProxy.class.getName());

    protected String name = "";

    protected RuleKey key;

    protected volatile boolean blockRequest;
    protected volatile boolean blockResponse;

    protected List<Interceptor> interceptors = new ArrayList<>();

    private RuleStatisticCollector ruleStatisticCollector = new RuleStatisticCollector();

    private boolean active;
    private String error;

    protected Router router;

    private SSLContext sslInboundContext;
    private SSLParser sslInboundParser;

    public AbstractProxy() {
    }

    public AbstractProxy(RuleKey ruleKey) {
        this.key = ruleKey;
    }

    public List<Interceptor> getInterceptors() {
        return interceptors;
    }

    @MCChildElement(allowForeign = true, order = 100)
    public void setInterceptors(List<Interceptor> interceptors) {
        this.interceptors = interceptors;
    }

    public String getName() {
        return name;
    }

    public RuleKey getKey() {
        return key;
    }

    public boolean isBlockRequest() {
        return blockRequest;
    }

    public boolean isBlockResponse() {
        return blockResponse;
    }

    /**
     * @description The name as shown in the Admin Console.
     * @default By default, a name will be automatically generated from the target host, port, etc.
     */
    @MCAttribute
    public void setName(String name) {
        this.name = name;
    }

    public void setKey(RuleKey ruleKey) {
        this.key = ruleKey;
    }

    /**
     * @description <i>legacy attribute</i> for usage by Membrane Monitor
     * @default false
     */
    @MCAttribute
    public void setBlockRequest(boolean blockStatus) {
        this.blockRequest = blockStatus;
    }

    /**
     * @description <i>legacy attribute</i> for usage by Membrane Monitor
     * @default false
     */
    @MCAttribute
    public void setBlockResponse(boolean blockStatus) {
        this.blockResponse = blockStatus;
    }

    /**
     * @description Configures the usage of inbound SSL (HTTPS).
     */
    @MCChildElement(order = 75, allowForeign = true)
    public void setSslInboundParser(SSLParser sslInboundParser) {
        this.sslInboundParser = sslInboundParser;
    }

    @Override
    public SSLContext getSslInboundContext() {
        return sslInboundContext;
    }

    protected void setSslInboundContext(SSLContext sslInboundContext) {
        this.sslInboundContext = sslInboundContext;
    }

    @Override
    public SSLProvider getSslOutboundContext() {
        return null;
    }

    /**
     * Called after parsing is complete and this has been added to the object tree (whose root is Router).
     */
    public final void init(Router router) throws Exception {
        this.router = router;
        try {
            init();
            for (Interceptor i : interceptors)
                i.init(router);
            active = true;
        } catch (Exception e) {
            if (!router.isRetryInit())
                throw e;
            log.error("", e);
            active = false;
            error = e.getMessage();
        }
    }

    public void init() throws Exception {
        if (sslInboundParser == null)
            return;

        if (sslInboundParser.getAcme() != null) {
            if (!(key instanceof AbstractRuleKey))
                throw new RuntimeException("<acme> only be used inside of <serviceProxy> and similar rules.");
            String[] host = key.getHost().split(" +");
            AcmeSSLContext acmeCtx = (AcmeSSLContext) getSslInboundContext(); // TODO: remove this.
            // getSslInboundContext() of an inactive rule should not be called in the first place.
            if (acmeCtx == null)
                acmeCtx = new AcmeSSLContext(sslInboundParser, host, router.getHttpClientFactory(), router.getTimerManager());
            setSslInboundContext(acmeCtx);
            acmeCtx.init(router.getKubernetesClientFactory(), router.getHttpClientFactory());
            return;
        }
        sslInboundContext = generateSslInboundContext();
    }

    public boolean isTargetAdjustHostHeader() {
        return false;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public String getErrorState() {
        return error;
    }

    @Override
    public AbstractProxy clone() throws CloneNotSupportedException {
        AbstractProxy clone = (AbstractProxy) super.clone();
        try {
            clone.init(router);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return clone;
    }

    @Override
    public RuleStatisticCollector getStatisticCollector() {
        return ruleStatisticCollector;
    }

    @Override
    public String toString() { // TODO toString, getName, setName und name=""
        // Initialisierung vereinheitlichen.
        return getName();
    }

    private @NotNull SSLContext generateSslInboundContext() {
        if (sslInboundParser.getKeyGenerator() != null)
            return new GeneratingSSLContext(sslInboundParser, router.getResolverMap(), router.getBaseLocation());
        return new StaticSSLContext(sslInboundParser, router.getResolverMap(), router.getBaseLocation());
    }
}
