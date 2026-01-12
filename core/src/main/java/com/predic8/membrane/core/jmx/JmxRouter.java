/*
 * Copyright 2026 predic8 GmbH, www.predic8.com
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

import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import org.springframework.jmx.export.annotation.*;

import static com.predic8.membrane.core.jmx.JmxExporter.JMX_NAMESPACE;

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
        return router.getConfiguration().getJmx();
    }

    private void exportServiceProxyList(){
        for(Proxy proxy : router.getRuleManager().getRules()){
            if(proxy instanceof ServiceProxy){
                exportServiceProxy((ServiceProxy) proxy);
            }
        }
    }

    private void exportServiceProxy(ServiceProxy rule) {
        String prefix = JMX_NAMESPACE + ":00=apis, 01=%s, name=".formatted(router.getConfiguration().getJmx());
        exporter.addBean(prefix + rule.getName().replace(":",""), new JmxServiceProxy(rule, router));
    }
}
