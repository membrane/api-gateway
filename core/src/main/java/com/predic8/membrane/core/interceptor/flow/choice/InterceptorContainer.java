package com.predic8.membrane.core.interceptor.flow.choice;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.core.interceptor.Interceptor;

import java.util.List;

abstract class InterceptorContainer {

    private List<Interceptor> interceptors;

    public List<Interceptor> getInterceptors() {
        return interceptors;
    }

    @MCChildElement(allowForeign = true)
    public void setInterceptors(List<Interceptor> interceptors) {
        this.interceptors = interceptors;
    }
}
