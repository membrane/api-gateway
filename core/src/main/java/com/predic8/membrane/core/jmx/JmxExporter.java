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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.router.*;
import org.slf4j.*;
import org.springframework.beans.factory.*;
import org.springframework.context.*;
import org.springframework.jmx.export.*;
import org.springframework.jmx.export.annotation.*;
import org.springframework.jmx.export.assembler.*;

import java.util.*;

import static org.springframework.jmx.support.RegistrationPolicy.*;

@MCElement(name = JmxExporter.JMX_EXPORTER_NAME)
public class JmxExporter implements Lifecycle {

    private static final Logger log = LoggerFactory.getLogger(JmxExporter.class);

    public static final String JMX_EXPORTER_NAME = "jmxExporter";
    public static final String JMX_NAMESPACE = "io.membrane-api";

    private final HashMap<String, Object> jmxBeans = new HashMap<>();

    private MBeanExporter exporter;

    @Override
    public void start() {
        exporter = new MBeanExporter();
        exporter.setRegistrationPolicy(IGNORE_EXISTING);
        MetadataMBeanInfoAssembler assembler = new MetadataMBeanInfoAssembler();
        assembler.setAttributeSource(new AnnotationJmxAttributeSource());
        assembler.afterPropertiesSet();
        exporter.setAssembler(assembler);
    }

    @Override
    public void stop() {
        jmxBeans.clear();
        exporter.destroy();
        exporter = null;
    }

    @Override
    public boolean isRunning() {
        return exporter != null;
    }

    public void addBean(String fullyQualifiedMBeanName, Object bean) {
        jmxBeans.put(fullyQualifiedMBeanName, bean);
    }

    public void initAfterBeansAdded() {
        // Temporary workaround till YAML start calls start() on components
        if (exporter == null)
            start();

        exporter.setBeans(jmxBeans);
        exporter.afterPropertiesSet();
        exporter.afterSingletonsInstantiated();
    }

    public void addRouter(Router router) {
        addBean(JMX_NAMESPACE + ":00=routers, name=" + router.getConfiguration().getJmx(), new JmxRouter(router, this));
        initAfterBeansAdded();
    }


    public static void start(Router router) {
        var eo = getExporter(router);
        if (eo.isEmpty()) {
            log.debug("Not starting JMX exporter. Declare component in YAML or element in XML");
            return;
        }
        log.debug("Starting JMX.");
        eo.get().addRouter(router);
    }

    public static Optional<JmxExporter> getExporter(Router router) {
        var exporter = router.getRegistry().getBean(JmxExporter.class);
        if (exporter.isPresent()) return exporter;
        if (router.getBeanFactory() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(router.getBeanFactory().getBean(JMX_EXPORTER_NAME, JmxExporter.class));
        } catch (NoSuchBeanDefinitionException e) {
            return Optional.empty();
        }
    }
}
