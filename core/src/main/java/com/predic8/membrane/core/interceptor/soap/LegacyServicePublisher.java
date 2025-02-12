package com.predic8.membrane.core.interceptor.soap;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.wsdl.Definitions;
import com.predic8.wsdl.WSDLParser;

import java.util.List;

@MCElement(name = "legacyServicePublisher")
public class LegacyServicePublisher extends AbstractInterceptor {

    private String wsdlLocation;
    private List<WebServiceOASWrapper> services;

    @Override
    public void init() {
        super.init();
        new WSDLParser().parse(wsdlLocation).getServices().forEach(svc ->
            services.add(new WebServiceOASWrapper(svc))
        );
    }

    @Override
    public Outcome handleRequest(Exchange exc) {

    }

    @Override
    public Outcome handleResponse(Exchange exc) {

    }

    @MCAttribute
    public void setWsdlLocation(String wsdlLocation) {
        this.wsdlLocation = wsdlLocation;
    }

    public String getWsdlLocation() {
        return wsdlLocation;
    }
}
