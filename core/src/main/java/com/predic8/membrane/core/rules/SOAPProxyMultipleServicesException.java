package com.predic8.membrane.core.rules;

import java.util.*;

public class SOAPProxyMultipleServicesException extends RuntimeException  {

    private SOAPProxy soapProxy;
    private List<String> services;

    public SOAPProxyMultipleServicesException(SOAPProxy soapProxy, List<String> services) {
        this.soapProxy = soapProxy;
        this.services = services;
    }

    public SOAPProxy getSoapProxy() {
        return soapProxy;
    }

    public List<String> getServices() {
        return services;
    }
}
