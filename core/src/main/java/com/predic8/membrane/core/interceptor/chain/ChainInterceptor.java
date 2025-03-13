package com.predic8.membrane.core.interceptor.chain;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.AbstractFlowInterceptor;
import com.predic8.membrane.core.util.ConfigurationException;

@MCElement(name = "chain")
public class ChainInterceptor extends AbstractFlowInterceptor {

    private String id;

    @Override
    public void init() {
        if (id == null) return;
        interceptors = router.getBeanFactory()
                .getBeansOfType(Chain.class)
                .values()
                .stream()
                .filter(e -> e.getId().equals(id))
                .findFirst()
                .orElseThrow(() ->
                        new ConfigurationException("No chain with reference %s found".formatted(id))
                )
                .getInterceptors();

        super.init();
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return router.getFlowController().invokeRequestHandlers(exc, interceptors);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return router.getFlowController().invokeResponseHandlers(exc, interceptors);
    }

    public String getId() {
        return id;
    }

    @MCAttribute
    public void setId(String id) {
        this.id = id;
    }

}
