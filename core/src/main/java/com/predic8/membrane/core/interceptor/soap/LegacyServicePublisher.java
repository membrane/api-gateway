package com.predic8.membrane.core.interceptor.soap;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisher;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.wsdl.Definitions;
import com.predic8.wsdl.WSDLParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.http.Request.METHOD_GET;
import static com.predic8.membrane.core.http.Request.METHOD_POST;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisher.PATTERN_UI;
import static com.predic8.membrane.core.util.CollectionsUtil.mapOf;
import static java.lang.String.valueOf;
import static java.util.stream.Collectors.toMap;

@MCElement(name = "legacyServicePublisher")
public class LegacyServicePublisher extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LegacyServicePublisher.class);

    private final List<WebServiceOASWrapper> services = new ArrayList<>();

    private String wsdlLocation;
    private OpenAPIPublisher publisher;

    @Override
    public void init() {
        super.init();
        Definitions defs = new WSDLParser().parse(wsdlLocation);
        defs.getServices().forEach(svc -> services.add(new WebServiceOASWrapper(defs, svc)));
        try {
            publisher = new OpenAPIPublisher(mapOf(services.stream().flatMap(WebServiceOASWrapper::getApiRecords)));
        } catch (Exception e) {
            log.error("OpenAPI Publisher failed to initialize.", e);
            throw new ConfigurationException("Unable to initialize Swagger UI.", e);
        }
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (exc.getRequest().getMethod().equals(METHOD_GET)) {
            return handleOpenAPIServing(exc);
        }
        if (exc.getRequest().getMethod().equals(METHOD_POST)) {
            handleServiceRouting();
        }
        ProblemDetails.user(
                router.isProduction(),
                getDisplayName()
        ).title("Invalid HTTP method").buildAndSetResponse(exc);
        return RETURN;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return CONTINUE;
    }

    private void handleServiceRouting() {
    }

    private Outcome handleOpenAPIServing(Exchange exc) {
        if (exc.getRequest().getUri().matches(valueOf(PATTERN_UI))) {
            return publisher.handleSwaggerUi(exc);
        }

        try {
            return publisher.handleOverviewOpenAPIDoc(exc, router, log);
        } catch (Exception e) {
            log.error("", e);
            internal(router.isProduction(),getDisplayName())
                    .detail("Error generating OpenAPI overview!")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    @MCAttribute
    public void setWsdl(String wsdlLocation) {
        this.wsdlLocation = wsdlLocation;
    }

    public String getWsdl() {
        return wsdlLocation;
    }
}
