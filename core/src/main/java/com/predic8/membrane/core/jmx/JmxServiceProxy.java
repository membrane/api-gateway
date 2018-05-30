/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.jmx;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.ServiceProxy;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

import java.io.IOException;

@ManagedResource
public class JmxServiceProxy {

    private final ServiceProxy rule;
    private final Router router;

    public JmxServiceProxy(ServiceProxy rule, Router router) {
        this.rule = rule;
        this.router = router;
    }

    @ManagedOperation
    public void toggleActive() throws IOException {
        if(isActive()) {
            router.getRuleManager().removeRule(rule);
        }
        else {
            router.getRuleManager().addProxyAndOpenPortIfNew(rule);
        }
        router.getRuleManager().ruleChanged(rule);
    }

    @ManagedAttribute
    public boolean isActive(){
        return router.getRuleManager().exists(rule.getKey());
    }

    @ManagedAttribute
    public int getProcessedExchanges(){
        return rule.getStatisticCollector().getCount();
    }
}
