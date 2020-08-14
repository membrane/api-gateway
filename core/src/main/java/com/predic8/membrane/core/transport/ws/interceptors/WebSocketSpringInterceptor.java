/*
 *  Copyright 2017 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.transport.ws.interceptors;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface;
import com.predic8.membrane.core.transport.ws.WebSocketSender;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

@MCElement(name="wsInterceptor")
public class WebSocketSpringInterceptor implements ApplicationContextAware, WebSocketInterceptorInterface {

    private String refid;
    private WebSocketInterceptorInterface i;
    private ApplicationContext ac;

    /**
     * @description Spring bean id of the referenced interceptor.
     * @example myInterceptor
     */
    @Required
    @MCAttribute(attributeName="refid")
    public void setRefId(String refid) {
        this.refid = refid;
    }

    public String getRefId() {
        return refid;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ac = applicationContext;
        i = ac.getBean(refid, WebSocketInterceptorInterface.class);
    }

    @Override
    public void init(Router router) throws Exception {
        i.init(router);
    }

    @Override
    public void handleFrame(WebSocketFrame frame, boolean frameTravelsToRight, WebSocketSender sender) throws Exception {
        i.handleFrame(frame,frameTravelsToRight,sender);
    }
}
