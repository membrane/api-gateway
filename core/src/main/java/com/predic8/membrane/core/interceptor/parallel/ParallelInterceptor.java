package com.predic8.membrane.core.interceptor.parallel;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.parallel.CollectionStrategy.CollectionStrategyElement;
import com.predic8.membrane.core.rules.AbstractServiceProxy.Target;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@MCElement(name="parallel")
public class ParallelInterceptor extends AbstractInterceptor {

    private List<Target> targets = new ArrayList<>();
    private CollectionStrategyElement strategy;

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {

        List<Exchange> exchanges = new ArrayList<>();

        exc.getRequest().getHeader().getAllHeaderFields();

        CollectionStrategy cs = strategy.getNewInstance();
        CompletableFuture<Exchange> future = CompletableFuture.supplyAsync(() -> {
            return cs.handleExchanges(exchanges);
        });
        Exchange result = future.join();

        // TODO Somehow override
        return Outcome.RETURN;
    }

    @MCChildElement
    public void setTargets(List<Target> targets) {
        this.targets = targets;
    }

    public List<Target> getTargets() {
        return targets;
    }

    private static Exchange cloneExchange(Exchange exc) {
        Exchange cloned = new Exchange(exc.getHandler());
        cloned.setRequest(cloneRequest(exc.getRequest()));
    }

    private static Request cloneRequest(Request request) throws IOException {
        Request cloned = new Request();
        cloned.setMethod(request.getMethod());
        cloned.setUri(request.getUri());

        for(HeaderField field: request.getHeader().getAllHeaderFields()) {
            cloned.getHeader().add(new HeaderField(field.getHeaderName().getName(), field.getValue()));
        }

        cloned.setBodyContent(request.getBodyAsStreamDecoded().readAllBytes());

    }

}
