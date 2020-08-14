/*
 * Copyright 2015 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.interceptor.apimanagement.apiconfig;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.spring.BaseLocationApplicationContext;
import com.predic8.membrane.core.interceptor.apimanagement.ApiManagementConfiguration;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;

@MCElement(name="simpleApiConfig")
public class SimpleApiConfig implements Lifecycle, ApplicationContextAware, ApiConfig, DisposableBean {

    ApiManagementConfiguration amc;
    private String url;
    private ApplicationContext context;

    @Override
    public ApiManagementConfiguration getConfiguration() {
        return amc;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    @Override
    public void start() {
        setUrl(this.url);
    }

    @Override
    public void stop() {
        amc.shutdown();
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    public String getUrl() {
        return url;
    }

    /**
     * @description the url to the configuration file
     * @default api.yaml
     */
    @MCAttribute
    public void setUrl(String url) {
        this.url = url;
        if(context != null) {
            if (amc == null) {
                String workingDir = ((BaseLocationApplicationContext) context).getBaseLocation();
                amc = new ApiManagementConfiguration(workingDir, this.url);
            } else {
                amc.setLocation(this.url);
            }
        }
    }

    @Override
    public void destroy() throws Exception {
        stop();
    }
}
