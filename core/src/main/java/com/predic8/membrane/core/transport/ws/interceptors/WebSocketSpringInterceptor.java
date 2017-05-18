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
