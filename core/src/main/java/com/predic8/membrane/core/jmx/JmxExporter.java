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
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;
import org.springframework.jmx.export.MBeanExporter;
import org.springframework.jmx.export.annotation.AnnotationJmxAttributeSource;
import org.springframework.jmx.export.assembler.MetadataMBeanInfoAssembler;

import java.util.HashMap;
import java.util.Map;

@MCElement(name="jmx")
public class JmxExporter implements Lifecycle, ApplicationContextAware, DisposableBean {

    ApplicationContext context;

    MBeanExporter exporter;


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    @Override
    public void start() {
        exporter = new MBeanExporter();
        exporter.setBeans(collectBeans());
        exporter.afterPropertiesSet();
        exporter.afterSingletonsInstantiated();
    }

    @Override
    public void stop() {

    }

    @Override
    public boolean isRunning() {
        return false;
    }

    public Map<String,Object> collectBeans(){
        HashMap<String,Object> result = new HashMap<String, Object>();
        result.put("bean:name=Router", new JmxRouter(context));
        return result;
    }

    @Override
    public void destroy() throws Exception {
        exporter.destroy();
    }
}
