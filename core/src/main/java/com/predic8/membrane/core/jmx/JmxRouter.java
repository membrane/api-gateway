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
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

import java.util.ArrayList;

@ManagedResource()
public class JmxRouter {


    private final Router router;
    private final JmxExporter exporter;

    public JmxRouter(Router router, JmxExporter exporter) {
        this.router = router;
        this.exporter = exporter;
        exportServiceProxyList();
    }

    @ManagedAttribute
    public String getName(){
        return router.getJmx();
    }

    private void exportServiceProxyList(){
        for(Rule rule : router.getRules()){
            if(rule instanceof ServiceProxy){
                exportServiceProxy((ServiceProxy) rule);
            }
        }
    }

    private void exportServiceProxy(ServiceProxy rule) {
        String prefix = "org.membrane-soa:00=serviceProxies, 01=" + router.getJmx()+ ", name=";
        exporter.addBean(prefix + rule.getName().replace(":",""), new JmxServiceProxy(rule, router));
    }
}
