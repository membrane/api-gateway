package com.predic8.membrane.core.interceptor.chain;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.interceptor.Interceptor;

import java.util.List;

@MCElement(name = "chainDef")
public class Chain {

    private String id;

    List<Interceptor> interceptors;


    @MCChildElement
    public void setInterceptors(List<Interceptor> interceptors) {
        this.interceptors = interceptors;
    }

    public List<Interceptor> getInterceptors() {
        return interceptors;
    }

    @MCAttribute
    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

}
