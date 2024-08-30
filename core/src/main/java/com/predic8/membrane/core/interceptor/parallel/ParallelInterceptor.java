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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.predic8.membrane.core.http.Header.CONTENT_ENCODING;

@MCElement(name="parallel")
public class ParallelInterceptor extends AbstractInterceptor {

    public static final String PARALLEL_TARGET_ID = "parallel_target_id";
    private List<Target> targets = new ArrayList<>();
    private CollectionStrategyElement strategy;

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        exc.setResponse(CompletableFuture.supplyAsync(() ->
                strategy.getNewInstance().handleExchanges(duplicateExchangeByTargets(
                        exc,
                        exc.getRequest().getBodyAsStringDecoded(),
                        targets
                ))
        ).join().getResponse());
        return Outcome.RETURN;
    }

    static List<Exchange> duplicateExchangeByTargets(Exchange exc, String body, List<Target> targetList) {
        List<Exchange> exchanges = new ArrayList<>();
        for (Target target : targetList) {
            if(target.getPort() == -1)
                target.setPort(target.getSslParser() != null ? 443 : 80);
            Exchange newExc = cloneExchange(exc, body);
            newExc.setDestinations(new ArrayList<>(List.of(getUrlFromTarget(target))));
            newExc.setProperty(PARALLEL_TARGET_ID, target.getId());
            exchanges.add(newExc);
        }
        return exchanges;
    }

    static String getUrlFromTarget(Target target) {
        if (target.getUrl() != null) {
            return target.getUrl();
        }
        String protocol = (target.getSslParser() != null) ? "https://" : "http://";
        return String.format("%s%s:%d", protocol, target.getHost(), target.getPort());
    }


    @MCChildElement(order = 1)
    public void setTargets(List<Target> targets) {
        this.targets = targets;
    }

    public List<Target> getTargets() {
        return targets;
    }

    @MCChildElement
    public void setStrategy(CollectionStrategyElement strategy) {
        this.strategy = strategy;
    }

    public CollectionStrategyElement getStrategy() {
        return strategy;
    }

    private static Exchange cloneExchange(Exchange exc, String body) {
        Exchange cloned = new Exchange(exc.getHandler());
        cloned.setRequest(cloneRequest(exc.getRequest(), body));
        return cloned;
    }

    static Request cloneRequest(Request request, String body) {
        Request cloned = new Request();
        cloned.setMethod(request.getMethod());
        cloned.setUri(request.getUri());

        for(HeaderField field: request.getHeader().getAllHeaderFields()) {
            cloned.getHeader().add(new HeaderField(field.getHeaderName().getName(), field.getValue()));
        }
        if (request.getBodyAsStringDecoded() != null)
            cloned.setBodyContent(body.getBytes());

        cloned.getHeader().removeFields(CONTENT_ENCODING);

        return cloned;
    }

}
