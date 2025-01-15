package com.predic8.membrane.core.interceptor.flow.choice;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.AbstractFlowInterceptor;
import com.predic8.membrane.core.lang.ExchangeExpressionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name = "choose")
public class ChoiceInterceptor extends AbstractFlowInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ChoiceInterceptor.class);
    private final List<Case> cases = new ArrayList<>();
    private Otherwise otherwise;

    @Override
    public void init() {
        cases.forEach(c -> c.init(router));
        interceptors.addAll(otherwise.getInterceptors());
        interceptors.addAll(cases.stream()
                .map(InterceptorContainer::getInterceptors)
                .flatMap(Collection::stream)
                .toList()
        );
        super.init();
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc, RESPONSE);
    }

    private Outcome handleInternal(Exchange exc, Flow flow) {
        Case choice = null;
        for (Case c : cases) {
            if (evaluateCase(c, exc, flow)) {
                choice = c;
                break;
            }
        }

        if (choice == null)
            return invokeInterceptorsAndReturn(otherwise.getInterceptors(), exc, flow);

        return invokeInterceptorsAndReturn(choice.getInterceptors(), exc, flow);
    }

    private Outcome invokeInterceptorsAndReturn(List<Interceptor> interceptors, Exchange exc, Flow flow) {
        try {
            return switch (flow) {
                case REQUEST -> getFlowController().invokeRequestHandlers(exc, interceptors);
                case RESPONSE -> getFlowController().invokeResponseHandlers(exc, interceptors);
                default -> throw new RuntimeException("Should never happen");
            };
        } catch (Exception e) {
            ProblemDetails.internal(router.isProduction())
                .detail("Error invoking plugin: " + e.getLocalizedMessage())
                .component(e.getClass().getSimpleName())
                .buildAndSetResponse(exc);
            throw new ExchangeExpressionException("Error evaluating expression on exchange in if plugin.", e);
        }
    }

    private boolean evaluateCase(Case c, Exchange exc, Flow flow) {
        try {
            boolean result = c.getExchangeExpression().evaluate(exc, flow, Boolean.class);
            if (log.isDebugEnabled())
                log.debug("Expression {} evaluated to {}.", c.getTest(), result);
            return result;
        } catch (ExchangeExpressionException e) {
            e.provideDetails(ProblemDetails.internal(router.isProduction()))
                    .detail("Error evaluating expression on exchange in if plugin.")
                    .component("if")
                    .buildAndSetResponse(exc);
            throw new ExchangeExpressionException("Error evaluating expression on exchange in if plugin.", e);
        } catch (NullPointerException npe) {
            // Expression evaluated to null and can't be converted to boolean
            // We assume that null is false
            log.debug("Expression {} returned null and is therefore interpreted as false", c.getTest());
            return false;
        }
    }

    @MCChildElement
    public void setCases(List<Case> cases) {
        this.cases.addAll(cases);
    }

    @MCChildElement
    public void setOtherwise(Otherwise otherwise) {
        this.otherwise = otherwise;
    }

    @MCChildElement(allowForeign = true)
    public void setInterceptors(List<Interceptor> interceptors) {
        // We use <case> and <otherwise> child elements to set interceptors, not child interceptors.
    }
}
