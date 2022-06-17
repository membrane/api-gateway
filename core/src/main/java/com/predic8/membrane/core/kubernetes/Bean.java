package com.predic8.membrane.core.kubernetes;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

/**
 * @description
 * "bean" should be used for Kubernetes only. Experimental.
 */
@MCElement(name = "bean")
public class Bean {

    Object bean;

    public Object getBean() {
        return bean;
    }

    @MCChildElement
    public void setBean(Object bean) {
        this.bean = bean;
    }
}
