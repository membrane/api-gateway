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
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.beanregistry.BeanDefinition;
import com.predic8.membrane.annot.beanregistry.BeanDefinitionAware;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.InterceptorUtil;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.stats.RuleStatisticCollector;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.predic8.membrane.core.util.BeanDefinitionBasePathUtil.resolveBaseLocation;

/**
 * Convenience class that implements Proxy.
 */
public abstract class AbstractProxy implements Proxy, BeanDefinitionAware {

    private static final Logger log = LoggerFactory.getLogger(AbstractProxy.class.getName());

    protected String name = "";
    protected RuleKey key;

    protected List<Interceptor> interceptors = new ArrayList<>();

    private final RuleStatisticCollector ruleStatisticCollector = new RuleStatisticCollector();

    private boolean active;
    private String error;

    protected Router router;
    private BeanDefinition beanDefinition;

    public AbstractProxy() {
    }

    public AbstractProxy(RuleKey ruleKey) {
        this.key = ruleKey;
    }

    protected  <T extends Interceptor> Optional<T> getFirstInterceptorOfType(Class<T> type) {
        return InterceptorUtil.getFirstInterceptorOfType(interceptors, type);
    }

    public List<Interceptor> getFlow() {
        return interceptors;
    }

    @MCChildElement(allowForeign = true, order = 100)
    public void setFlow(List<Interceptor> flow) {
        this.interceptors = flow;
    }

    public String getName() {
        return StringUtils.defaultIfEmpty(name, getKey() != null ? getKey().toString() : "");
    }

    public RuleKey getKey() {
        return key;
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
     * Called after parsing is complete and this has been added to the object tree (whose root is Router).
     */
    public final void init(Router router) {
        this.router = router;
        try {
            init(); // Extension point for subclasses
            assignBeanDefinitionToInterceptors();
            for (var i : interceptors) {
                i.init(router, this);
            }
            active = true;
        } catch (Exception e) {
            if (!router.getConfiguration().isRetryInit())
                throw e;
            log.error("", e);
            active = false;
            error = e.getMessage();
        }
    }

    /**
     *  Extension point for subclasses
     */
    public void init() {
    }

    private void assignBeanDefinitionToInterceptors() {
        if (beanDefinition == null) {
            return;
        }

        for (Interceptor interceptor : interceptors) {
            if (interceptor instanceof BeanDefinitionAware bda && bda.getBeanDefinition() == null) {
                bda.setBeanDefinition(beanDefinition);
            }
        }
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
    public void setBeanDefinition(BeanDefinition beanDefinition) {
        this.beanDefinition = beanDefinition;
    }

    @Override
    public BeanDefinition getBeanDefinition() {
        return beanDefinition;
    }

    protected final String getBeanBaseLocation() {
        return resolveBaseLocation(this, router);
    }

    @Override
    public String getProtocol() {
        return "unknown";
    }

    @Override
    public String toString() {
        // Initialisierung vereinheitlichen.
        return getName();
    }
}
