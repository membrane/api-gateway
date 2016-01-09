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

import com.predic8.membrane.annot.MCElement;
import org.apache.commons.logging.Log;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;
import org.springframework.jmx.export.MBeanExporter;
import org.springframework.jmx.export.annotation.AnnotationJmxAttributeSource;
import org.springframework.jmx.export.assembler.MetadataMBeanInfoAssembler;
import org.springframework.jmx.export.metadata.JmxAttributeSource;
import org.springframework.jmx.support.MBeanRegistrationSupport;
import org.springframework.jmx.support.RegistrationPolicy;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@MCElement(name=JmxExporter.JMX_EXPORTER_NAME)
public class JmxExporter extends MBeanExporter implements Lifecycle, ApplicationContextAware, DisposableBean {

    public static final String JMX_EXPORTER_NAME = "jmxExporter";
    HashMap<String, Object> jmxBeans = new HashMap<String, Object>();

    ApplicationContext context;

    MBeanExporter exporter;


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    @Override
    public void start() {
        exporter = new MBeanExporter();
        exporter.setRegistrationPolicy(RegistrationPolicy.IGNORE_EXISTING);
        MetadataMBeanInfoAssembler assembler = new MetadataMBeanInfoAssembler();
        assembler.setAttributeSource(new AnnotationJmxAttributeSource());
        assembler.afterPropertiesSet();
        exporter.setAssembler(assembler);
    }

    @Override
    public void stop() {

    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public void destroy() {
        jmxBeans.clear();
        exporter.destroy();
    }

    public void addBean(String fullyQualifiedMBeanName, Object bean ) {
        jmxBeans.put(fullyQualifiedMBeanName,bean);
    }

    public void removeBean(String fullyQualifiedMBeanName){
        jmxBeans.remove(fullyQualifiedMBeanName);
    }

    public void initAfterBeansAdded()
    {
        exporter.setBeans(jmxBeans);
        exporter.afterPropertiesSet();
        exporter.afterSingletonsInstantiated();
    }
}
