package com.predic8.membrane.core;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.util.URIFactory;

@MCElement(name = "configuration", topLevel = true, component = false)
public class Configuration {

    private boolean hotDeploy = true;

    private int retryInitInterval = 5 * 60 * 1000; // 5 minutes

    private boolean retryInit = true;

    private String jmxRouterName;

    private boolean production;

    private URIFactory uriFactory = new URIFactory(false);

    public boolean isHotDeploy() {
        return hotDeploy;
    }

    @MCAttribute
    public void setHotDeploy(boolean hotDeploy) {
        this.hotDeploy = hotDeploy;
    }

    public int getRetryInitInterval() {
        return retryInitInterval;
    }

    @MCAttribute
    public void setRetryInitInterval(int retryInitInterval) {
        this.retryInitInterval = retryInitInterval;
    }

    public boolean isRetryInit() {
        return retryInit;
    }

    @MCAttribute
    public void setRetryInit(boolean retryInit) {
        this.retryInit = retryInit;
    }

    public String getJmxRouterName() {
        return jmxRouterName;
    }

    @MCAttribute
    public void setJmxRouterName(String jmxRouterName) {
        this.jmxRouterName = jmxRouterName;
    }

    public boolean isProduction() {
        return production;
    }

    @MCAttribute
    public void setProduction(boolean production) {
        this.production = production;
    }


    public URIFactory getUriFactory() {
        return uriFactory;
    }

    @MCChildElement
    public void setUriFactory(URIFactory uriFactory) {
        this.uriFactory = uriFactory;
    }
}
