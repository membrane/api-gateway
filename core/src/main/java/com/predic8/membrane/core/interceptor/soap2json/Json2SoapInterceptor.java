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

package com.predic8.membrane.core.interceptor.soap2json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.predic8.membrane.annot.Constants.SoapVersion;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.SOAPUtil.FaultCode;
import com.predic8.membrane.core.util.wsdl.parser.Operation;
import com.predic8.membrane.core.util.wsdl.parser.WSDLParserException;
import com.predic8.membrane.core.util.xml.schema.UnmappableJsonException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.http.Header.SOAP_ACTION;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_SOAP_XML;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.soap2json.Soap2JsonInterceptor.OPERATION_PROPERTY;
import static com.predic8.membrane.core.util.SOAPUtil.FaultCode.Client;
import static com.predic8.membrane.core.util.SOAPUtil.FaultCode.Server;
import static com.predic8.membrane.core.util.SOAPUtil.createSOAPFaultResponse;
import static com.predic8.membrane.core.util.text.TextUtil.escapeControlCharsForLog;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.SOAP_12;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.INPUT;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.OUTPUT;
import static org.w3c.dom.DOMException.INVALID_CHARACTER_ERR;

/**
 * @description <p>
 * Converts a JSON body to the SOAP message of a WSDL operation: in the request flow to the
 * operation's request, in the response flow to its response. The XML Schema of the WSDL decides the
 * names, namespaces and order of the elements; a JSON array becomes a repeated element, properties
 * prefixed with <code>@</code> become XML attributes, <code>$value</code> becomes the text of an
 * element that also carries attributes, and <code>null</code> becomes <code>xsi:nil</code>. This
 * is the mapping <i>wsdl2openapi</i> applies to requests, and the reverse of <i>soap2Json</i>.
 * </p>
 * <p>
 * Without a configured operation, the operation recorded by <i>soap2Json</i> in the request flow is
 * used. Together they put a SOAP interface in front of a JSON backend, as in the example.
 * </p>
 * <p>
 * A converted request is sent with POST and carries the SOAPAction header (SOAP 1.1) or the
 * action in the Content-Type (SOAP 1.2) that the WSDL binding declares. A converted response has
 * status 200. A response of the backend with an error status becomes a SOAP Fault,
 * <code>Client</code> for a 4xx status and <code>Server</code> otherwise; what the backend sent is
 * not passed on. A fault has status 500, except a <code>Client</code> fault in SOAP 1.2, which has
 * status 400. Errors are reported in the format of the client: problem details (RFC 7807) for
 * the JSON client in the request flow, a SOAP Fault for the SOAP client in the response flow.
 * Messages without a body pass through unchanged. Only document/literal messages are supported.
 * Placed directly in a flow, it converts in both directions, so it usually goes under
 * <code>request</code> or <code>response</code>.
 * </p>
 * <pre>
 * json2Soap:
 *   wsdl: &lt;url or path&gt;
 *   [ service: &lt;service name&gt; ]
 *   [ port: &lt;port name&gt; ]
 *   [ operation: &lt;operation name&gt; ]
 * </pre>
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - request:
 *         - soap2Json:
 *             wsdl: cities.wsdl
 *     - response:
 *         - json2Soap:
 *             wsdl: cities.wsdl
 *   target:
 *     url: http://localhost:3000
 * </code></pre>
 * @topic 6. Web Services with SOAP and WSDL
 */
@MCElement(name = "json2Soap")
public class Json2SoapInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(Json2SoapInterceptor.class);

    private String wsdl;
    private String service;
    private String port;
    private String operation;

    /**
     * What converting the messages of one operation needs, built in init(). Requests must never
     * read the WSDL model itself — it is a Xerces DOM, which concurrent reads corrupt.
     *
     * @param requestContentType the Content-Type of the SOAP request; for SOAP 1.2 it carries the action
     * @param soapAction         the SOAPAction header of a SOAP 1.1 request; {@code null} for SOAP 1.2
     */
    private record OperationConversion(Json2SoapTransformer request,
                                       Json2SoapTransformer response,
                                       String requestContentType,
                                       String soapAction) {
        Json2SoapTransformer forFlow(Flow flow) {
            return flow == REQUEST ? request : response;
        }
    }

    /** Everything init() takes from the WSDL; replaced wholesale, so a re-init is safe. */
    private record Conversions(Map<String, OperationConversion> byOperation, SoapVersion soapVersion) {}

    private volatile Conversions conversions;

    public Json2SoapInterceptor() {
        name = "json 2 soap";
    }

    @Override
    public void init() {
        super.init();
        final var definitions = WsdlLoader.parseWsdl(router, getBeanBaseLocation(), wsdl);
        final var selectedPort = WsdlLoader.selectPort(definitions, wsdl, service, port);
        final var byOperation = new LinkedHashMap<String, OperationConversion>();
        try {
            final var schemasByNamespace = definitions.getSchemasByNamespace();
            for (final Operation op : selectedPort.operations()) {
                final var opName = op.getName();
                if (opName == null) continue;
                WsdlLoader.validateSoapAction(selectedPort, wsdl, opName);
                byOperation.put(opName, new OperationConversion(
                        new Json2SoapTransformer(selectedPort, opName, INPUT, schemasByNamespace),
                        new Json2SoapTransformer(selectedPort, opName, OUTPUT, schemasByNamespace),
                        selectedPort.requestContentType(opName),
                        selectedPort.soapActionHeader(opName)));
            }
        } catch (WSDLParserException e) {
            throw new ConfigurationException("Cannot read the operations of WSDL '%s': %s".formatted(wsdl, e.getMessage()), e);
        }
        if (operation != null && !byOperation.containsKey(operation)) {
            throw new ConfigurationException("Port '%s' of WSDL '%s' declares no operation '%s'. It declares: %s".formatted(
                    selectedPort.name(), wsdl, operation, String.join(", ", byOperation.keySet())));
        }
        conversions = new Conversions(Map.copyOf(byOperation),
                selectedPort.soapVersion() == SOAP_12 ? SoapVersion.SOAP12 : SoapVersion.SOAP11);
        log.info("Loaded WSDL from {} for port {}, converting {} operation(s)", wsdl, selectedPort.name(), byOperation.size());
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
        final var current = conversions;
        final var msg = exc.getMessage(flow);
        if (msg == null) {
            return CONTINUE;
        }
        // Before the body check: an error without a body is still an error to the SOAP client.
        if (flow == RESPONSE && !isSuccess(exc.getResponse().getStatusCode())) {
            return backendError(exc, current, exc.getResponse().getStatusCode());
        }
        if (msg.isBodyEmpty()) {
            return CONTINUE;
        }

        final var operationName = operation != null ? operation : exc.getProperty(OPERATION_PROPERTY, String.class);
        final var conversion = operationName == null ? null : current.byOperation().get(operationName);
        if (conversion == null) {
            return unknownOperation(exc, flow, current, operationName);
        }
        if (!msg.isJSON()) {
            return notJson(exc, flow, current, msg.getHeader().getContentType());
        }

        try {
            final var soap = conversion.forFlow(flow).transform(msg.getBodyAsStringDecoded());
            msg.setBodyContent(soap);
            if (flow == REQUEST) {
                prepareRequest(exc, conversion);
            } else {
                prepareResponse(exc, current);
            }
            return CONTINUE;
        } catch (ReadingBodyException e) {
            // FlowController answers a body that could not be read; this is not a conversion error.
            throw e;
        } catch (JsonProcessingException e) {
            return clientError(exc, flow, current, "Invalid JSON", "The body is not valid JSON.", e);
        } catch (UnmappableJsonException e) {
            // Its message is fixed text, written for the client.
            return clientError(exc, flow, current, "Invalid JSON", e.getMessage(), e);
        } catch (DOMException e) {
            // A key of the JSON is not a legal XML name. Every other DOM error is the gateway's own.
            if (e.code != INVALID_CHARACTER_ERR) return conversionFailed(exc, flow, current, operationName, e);
            return clientError(exc, flow, current, "Invalid field name",
                    "A field name in the body cannot be mapped to XML.", e);
        } catch (Exception e) {
            return conversionFailed(exc, flow, current, operationName, e);
        }
    }

    private static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    private static void prepareRequest(Exchange exc, OperationConversion conversion) {
        final var request = exc.getRequest();
        request.setMethod("POST");
        request.getHeader().setContentType(conversion.requestContentType());
        if (conversion.soapAction() != null) {
            request.getHeader().setSOAPAction(conversion.soapAction());
        } else {
            // SOAP 1.2 sends no SOAPAction header: one the client sent must not reach the service.
            request.getHeader().removeFields(SOAP_ACTION);
        }
    }

    private static void prepareResponse(Exchange exc, Conversions current) {
        final var response = exc.getResponse();
        // SOAP over HTTP answers a successful request with 200 (SOAP 1.1 section 6.2, WS-I R1124).
        response.setStatusCode(200);
        response.setStatusMessage("OK");
        response.getHeader().setContentType(current.soapVersion() == SoapVersion.SOAP12 ? APPLICATION_SOAP_XML : TEXT_XML);
    }

    /** The backend is not trusted: only its status is used, what it sent is dropped. */
    private Outcome backendError(Exchange exc, Conversions current, int status) {
        log.info("JSON response not converted: the backend answered with status {}", status);
        if (status >= 400 && status < 500) {
            return soapFault(exc, current, Client, "The service rejected the request.");
        }
        return soapFault(exc, current, Server, "The service could not complete the operation.");
    }

    private Outcome unknownOperation(Exchange exc, Flow flow, Conversions current, String operationName) {
        final var escaped = operationName == null ? null : escapeControlCharsForLog(operationName);
        if (escaped == null) {
            log.warn("JSON {} not converted: no operation is configured and the exchange property {} is not set",
                    flowNoun(flow), OPERATION_PROPERTY);
        } else {
            log.warn("JSON {} not converted: the WSDL declares no operation '{}'", flowNoun(flow), escaped);
        }
        if (flow == RESPONSE) {
            return soapFault(exc, current, Server, "The response could not be converted to SOAP.");
        }
        final var pd = internal(router.getConfiguration().isProduction(), getDisplayName())
                .flow(flow)
                .detail("The WSDL operation of the request is not known.")
                .stacktrace(false);
        if (escaped != null) pd.internal("operation", escaped);
        pd.buildAndSetResponse(exc);
        return ABORT;
    }

    private Outcome notJson(Exchange exc, Flow flow, Conversions current, String contentType) {
        final var escaped = escapeControlCharsForLog(String.valueOf(contentType));
        log.info("JSON {} not converted: Content-Type {} is not JSON", flowNoun(flow), escaped);
        if (flow == RESPONSE) {
            return soapFault(exc, current, Server, "The response could not be converted to SOAP.");
        }
        user(router.getConfiguration().isProduction(), getDisplayName())
                .flow(flow)
                .status(415)
                .title("Unsupported media type")
                .detail("Expected a JSON body, but got %s.".formatted(escaped), true)
                .stacktrace(false)
                .buildAndSetResponse(exc);
        return ABORT;
    }

    /** A body the JSON client got wrong: a 400 in the request flow; from the backend, a fault. */
    private Outcome clientError(Exchange exc, Flow flow, Conversions current, String title, String detail, Exception e) {
        final var reason = escapeControlCharsForLog(String.valueOf(e.getMessage()));
        log.info("JSON {} not converted: {}", flowNoun(flow), reason);
        log.debug("", e);
        if (flow == RESPONSE) {
            return soapFault(exc, current, Server, "The response could not be converted to SOAP.");
        }
        user(router.getConfiguration().isProduction(), getDisplayName())
                .flow(flow)
                .title(title)
                .detail(detail, true)
                .internal("reason", reason)
                .stacktrace(false)
                .buildAndSetResponse(exc);
        return ABORT;
    }

    private Outcome conversionFailed(Exchange exc, Flow flow, Conversions current, String operationName, Exception e) {
        final var reason = escapeControlCharsForLog(String.valueOf(e.getMessage()));
        log.warn("Could not convert JSON {} of operation '{}' to SOAP: {}", flowNoun(flow),
                escapeControlCharsForLog(operationName), reason);
        log.debug("", e);
        if (flow == RESPONSE) {
            return soapFault(exc, current, Server, "The response could not be converted to SOAP.");
        }
        internal(router.getConfiguration().isProduction(), getDisplayName())
                .flow(flow)
                .detail("The JSON request could not be converted to SOAP.")
                .internal("reason", reason)
                .stacktrace(false)
                .buildAndSetResponse(exc);
        return ABORT;
    }

    /** Answers the SOAP client with a fault; its code decides the status, see {@code createSOAPFaultResponse}. */
    private static Outcome soapFault(Exchange exc, Conversions current, FaultCode code, String faultString) {
        exc.setResponse(createSOAPFaultResponse(code, faultString, null, current.soapVersion()));
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
     * @description The name of the WSDL service whose messages are produced. Only needed when the
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
     * @description The name of the port of the service whose messages are produced. It also decides
     * the SOAP version. Only needed when the service has ports for different interfaces; of the
     * SOAP 1.1 and 1.2 ports of one interface, the SOAP 1.1 port is chosen.
     * @example CitySoap12Port
     */
    @MCAttribute
    public void setPort(String port) {
        this.port = port;
    }

    public String getOperation() {
        return operation;
    }

    /**
     * @description The WSDL operation whose message the JSON is converted to. Without it, the
     * operation is taken from the exchange property <code>membrane.soap.operation</code>, which
     * <i>soap2Json</i> sets in the request flow and which can also be set with <i>setProperty</i>.
     * A message whose operation is not known is not converted and is answered with an error.
     * @example getCity
     */
    @MCAttribute
    public void setOperation(String operation) {
        this.operation = operation;
    }

    @Override
    public String getShortDescription() {
        return operation == null
                ? "Converts JSON bodies to SOAP, using the WSDL %s.".formatted(wsdl)
                : "Converts JSON bodies to SOAP messages of operation %s, using the WSDL %s.".formatted(operation, wsdl);
    }
}
