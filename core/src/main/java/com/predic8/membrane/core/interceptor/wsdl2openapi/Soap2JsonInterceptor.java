/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.wsdl2openapi;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.WSDLParserException;
import com.predic8.membrane.core.util.xml.XMLInputSourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.util.text.TextUtil.escapeControlCharsForLog;

/**
 * @description <p>
 * Converts the Body of a SOAP 1.1 or 1.2 message to JSON, using the WSDL to map it. The element in
 * the Body selects the message of the WSDL, and its XML Schema decides the shape of the result:
 * numbers and booleans come out typed, an element that may repeat is always a JSON array, and
 * <code>xsi:nil</code> becomes <code>null</code>. XML attributes appear as properties prefixed with
 * <code>@</code>; the text of an element that also carries attributes appears under
 * <code>$value</code>. This is the mapping <i>wsdl2openapi</i> applies to SOAP responses.
 * </p>
 * <p>
 * A message the WSDL does not declare, a SOAP Fault, or XML that cannot be converted is answered
 * with a problem details document (RFC 7807): status 400 in the request flow, 500 in the response
 * flow. A body that is not XML, such as an MTOM/XOP package, is answered the same way, with status
 * 415 in the request flow. For a fault from a service, the content of a fault the WSDL declares appears under
 * <code>details</code>, keyed by the name of the declared fault. SOAP headers are not part of the
 * result. Only document/literal messages are supported. Messages without a body pass through
 * unchanged.
 * </p>
 * <p>
 * In the request flow, the operation the request belongs to is recorded in the exchange property
 * <code>membrane.soap.operation</code>, so that <i>json2Soap</i> can turn the JSON answer of a
 * backend back into that operation's SOAP response.
 * </p>
 * <pre>
 * soap2Json:
 *   wsdl: &lt;url or path&gt;
 *   [ service: &lt;service name&gt; ]
 *   [ port: &lt;port name&gt; ]
 * </pre>
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - response:
 *         - soap2Json:
 *             wsdl: cities.wsdl
 *   target:
 *     url: http://localhost:2001
 * </code></pre>
 * @topic 6. Web Services with SOAP and WSDL
 */
@MCElement(name = "soap2Json")
public class Soap2JsonInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(Soap2JsonInterceptor.class);

    /**
     * The exchange property holding the name of the WSDL operation of the exchange. Written by
     * soap2Json in the request flow, read by json2Soap when it has no configured operation.
     */
    public static final String OPERATION_PROPERTY = "membrane.soap.operation";

    private String wsdl;
    private String service;
    private String port;

    /**
     * Everything the conversion needs from the WSDL, built in init(). Requests must never read the
     * WSDL model itself — it is a Xerces DOM, which concurrent reads corrupt.
     */
    private volatile SoapBodySchemas bodySchemas;

    public Soap2JsonInterceptor() {
        name = "soap 2 json";
    }

    @Override
    public void init() {
        super.init();
        final var definitions = WsdlLoader.parseWsdl(router, getBeanBaseLocation(), wsdl);
        final var selectedPort = WsdlLoader.selectPort(definitions, wsdl, service, port);
        try {
            bodySchemas = SoapBodySchemas.of(selectedPort, new XsdToSchema(definitions, Set.of()));
        } catch (WSDLParserException e) {
            throw new ConfigurationException("Cannot read the messages of WSDL '%s': %s".formatted(wsdl, e.getMessage()), e);
        }
        log.info("Loaded WSDL from {} for port {}, converting {} message(s)", wsdl, selectedPort.name(),
                bodySchemas.bodySchemas().size());
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
        final var msg = exc.getMessage(flow);
        if (msg == null || msg.isBodyEmpty()) {
            return CONTINUE;
        }
        if (!msg.isXML()) {
            return notXml(exc, flow, msg.getHeader().getContentType());
        }
        final var schemas = bodySchemas;
        final var bodyElement = new AtomicReference<QName>();
        try {
            byte[] json;
            try (var body = msg.getBodyAsStreamDecoded()) {
                json = new Soap2JsonTransformer(schemas.components()).transform(
                        XMLInputSourceUtil.getInputSource(body, msg.getHeader().getCharset()),
                        element -> {
                            bodyElement.set(element);
                            return schemas.forElement(element);
                        },
                        schemas.faultDetailSchema());
            }
            msg.setBodyContent(json);
            msg.getHeader().setContentType(APPLICATION_JSON);
            if (flow == REQUEST) {
                schemas.inputOperation(bodyElement.get())
                        .ifPresent(operation -> exc.setProperty(OPERATION_PROPERTY, operation));
            }
            return CONTINUE;
        } catch (ReadingBodyException e) {
            // FlowController answers a body that could not be read; this is not a conversion error.
            throw e;
        } catch (SoapFaultException fault) {
            log.debug("SOAP fault in {} flow: [{}] {}", flow, fault.getFaultCode(), fault.getFaultMessage());
            return flow == REQUEST ? faultInRequest(exc) : faultInResponse(exc, fault);
        } catch (UndeclaredElementException e) {
            return undeclaredElement(exc, flow, e);
        } catch (Exception e) {
            return conversionFailed(exc, flow, e);
        }
    }

    /** A service answering with a fault failed — see {@link SoapFaultProblemDetails#of}. */
    private Outcome faultInResponse(Exchange exc, SoapFaultException fault) {
        exc.setResponse(SoapFaultProblemDetails.of(fault, router.getConfiguration().isProduction(), getDisplayName()));
        return ABORT;
    }

    /** A fault answers a request; a client sending one has sent no request at all. */
    private Outcome faultInRequest(Exchange exc) {
        log.info("SOAP request not converted: the Body carries a Fault");
        user(router.getConfiguration().isProduction(), getDisplayName())
                .flow(REQUEST)
                .title("Fault is not a request")
                .detail("The SOAP Body carries a Fault, which is not a request message.", true)
                .stacktrace(false)
                .buildAndSetResponse(exc);
        return ABORT;
    }

    /** A body that is not XML cannot be SOAP; passing it on would hand the next step a message it does not expect. */
    private Outcome notXml(Exchange exc, Flow flow, String contentType) {
        final var escaped = escapeControlCharsForLog(String.valueOf(contentType));
        log.info("SOAP {} not converted: Content-Type {} is not XML", flowNoun(flow), escaped);
        final var production = router.getConfiguration().isProduction();
        if (flow == REQUEST) {
            user(production, getDisplayName())
                    .flow(flow)
                    .status(415)
                    .title("Unsupported media type")
                    .detail("Expected a SOAP message with an XML Content-Type, but got %s.".formatted(escaped), true)
                    .stacktrace(false)
                    .buildAndSetResponse(exc);
        } else {
            // The backend is not trusted: what it sent stays internal.
            internal(production, getDisplayName())
                    .flow(flow)
                    .detail("The response of the service is not a SOAP message.")
                    .internal("contentType", escaped)
                    .stacktrace(false)
                    .buildAndSetResponse(exc);
        }
        return ABORT;
    }

    private Outcome undeclaredElement(Exchange exc, Flow flow, UndeclaredElementException e) {
        final var element = escapeControlCharsForLog(e.getElement().toString());
        log.info("SOAP {} not converted: {}", flowNoun(flow), escapeControlCharsForLog(e.getMessage()));
        if (flow == REQUEST) {
            // The client sent the element, so naming it tells the client nothing it does not know.
            user(router.getConfiguration().isProduction(), getDisplayName())
                    .flow(flow)
                    .title("Message not declared by the WSDL")
                    .detail("The SOAP Body carries the element %s, which the WSDL does not declare.".formatted(element), true)
                    .stacktrace(false)
                    .buildAndSetResponse(exc);
        } else {
            // The backend is not trusted: what it sent stays internal.
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .flow(flow)
                    .detail("The response of the service does not match the WSDL.")
                    .internal("element", element)
                    .stacktrace(false)
                    .buildAndSetResponse(exc);
        }
        return ABORT;
    }

    private Outcome conversionFailed(Exchange exc, Flow flow, Exception e) {
        final var reason = escapeControlCharsForLog(String.valueOf(e.getMessage()));
        log.info("Could not convert SOAP {} to JSON: {}", flowNoun(flow), reason);
        log.debug("", e);
        final var production = router.getConfiguration().isProduction();
        (flow == REQUEST ? user(production, getDisplayName()) : internal(production, getDisplayName()))
                .flow(flow)
                .detail("The SOAP %s could not be converted to JSON.".formatted(flowNoun(flow)))
                .internal("reason", reason)
                .stacktrace(false)
                .buildAndSetResponse(exc);
        return ABORT;
    }

    private static String flowNoun(Flow flow) {
        return flow == REQUEST ? "request" : "response";
    }

    public String getWsdl() {
        return wsdl;
    }

    /**
     * @description The WSDL that describes the SOAP messages: a URL or a file path, relative to the
     * configuration file.
     * @example cities.wsdl
     */
    @Required
    @MCAttribute
    public void setWsdl(String wsdl) {
        this.wsdl = wsdl;
    }

    public String getService() {
        return service;
    }

    /**
     * @description The name of the WSDL service whose messages are converted. Only needed when the
     * WSDL declares more than one service.
     * @example CityService
     */
    @MCAttribute
    public void setService(String service) {
        this.service = service;
    }

    public String getPort() {
        return port;
    }

    /**
     * @description The name of the port of the service whose messages are converted. Only needed when
     * the service has ports for different interfaces; of the SOAP 1.1 and 1.2 ports of one interface,
     * the SOAP 1.1 port is chosen.
     * @example CitySoap12Port
     */
    @MCAttribute
    public void setPort(String port) {
        this.port = port;
    }

    @Override
    public String getShortDescription() {
        return "Converts SOAP message bodies to JSON, using the WSDL %s.".formatted(wsdl);
    }
}
