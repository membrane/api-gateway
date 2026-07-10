/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.LSInputImpl;
import com.predic8.membrane.core.util.MessageUtil;
import com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion;
import com.predic8.membrane.core.util.xml.XMLUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.ls.LSResourceResolver;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.predic8.membrane.annot.Constants.SoapVersion.SOAP11;
import static com.predic8.membrane.annot.Constants.SoapVersion.SOAP12;
import static com.predic8.membrane.core.http.Header.VALIDATION_ERROR_SOURCE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.schemavalidation.WSDLMessageElementExtractor.getPossibleRequestElements;
import static com.predic8.membrane.core.interceptor.schemavalidation.WSDLMessageElementExtractor.getPossibleResponseElements;
import static com.predic8.membrane.core.util.SOAPUtil.FaultCode.Client;
import static com.predic8.membrane.core.util.SOAPUtil.*;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.SOAP_11;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.SOAP_12;
import static java.nio.charset.StandardCharsets.UTF_8;

public class WSDLValidator extends AbstractXMLSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(WSDLValidator.class.getName());

    /**
     * List of toplevel soapElements that are valid for requests
     */
    private final Set<QName> requestElements;

    /**
     * List of toplevel soapElements that are valid for responses
     */
    private final Set<QName> responseElements;

    private final Set<SOAPVersion> versions;

    private final boolean skipFaults;

    /**
     * Parsed WSDL document
     */
    private final com.predic8.membrane.core.util.wsdl.parser.Definitions definitions;

    public WSDLValidator(ResolverMap resourceResolver, String location, String serviceName, ValidatorInterceptor.FailureHandler failureHandler, boolean skipFaults) {
        super(resourceResolver, location, failureHandler);
        this.skipFaults = skipFaults;

        try {
            definitions = com.predic8.membrane.core.util.wsdl.parser.Definitions.parse(resourceResolver, location);
        } catch (ResourceRetrievalException e) {
            throw new ConfigurationException("""
                    Could not extract embedded schemas from WSDL at location %s.
                    """.formatted(location), e);
        } catch (Exception e) {
            throw new ConfigurationException("""
                    Could not parse WSDL as XML document at location %s.
                    Error Message: %s
                    """.formatted(location, e.getMessage()));
        }

        requestElements = getPossibleRequestElements(definitions, serviceName);
        responseElements = getPossibleResponseElements(definitions, serviceName);
        versions = definitions.getSoapVersions();
    }

    @Override
    public String getName() {
        return "wsdl-validator";
    }

    @Override
    public Outcome validateMessage(Exchange exc, Interceptor.Flow flow) throws Exception {
        var message = exc.getMessage(flow);

        if (flow == Interceptor.Flow.RESPONSE && message.isBodyEmpty() ) {
            log.info("Skipping validation of empty response.");
            return CONTINUE;
        }

        var result = analyseSOAPMessage(xopr, message);

        if (!result.isSOAP()) {
            setErrorResponse(exc, "Not a valid SOAP message.");
            exc.getResponse().getHeader().add(VALIDATION_ERROR_SOURCE, flow.name());
            return ABORT;
        }

        if (result.isFault() && skipFaults) {
            log.debug("Skipping validation of fault message.");
            return CONTINUE;
        }

        if (!versions.isEmpty()) {
            if (result.version() == SOAP11 && !versions.contains(SOAP_11)) {
                setErrorResponse(exc, "SOAP version 1.1 is not valid");
                return ABORT;
            }
            if (result.version() == SOAP12 && !versions.contains(SOAP_12)) {
                setErrorResponse(exc, "SOAP version 1.2 is not valid");
                return ABORT;
            }
        }

        if (flow == Interceptor.Flow.REQUEST && !isPossibleRequestElement(result.soapElement())) {
            setErrorResponse(exc, "%s is not a valid request element. Possible elements are %s".formatted(result.soapElement(), requestElements));
            return ABORT;
        }
        if (flow == Interceptor.Flow.RESPONSE && !result.isFault() && !isPossibleResponseElement(result.soapElement())) {
            setErrorResponse(exc, "%s is not a valid response element. Possible elements are %s".formatted(result.soapElement(), responseElements));
            return ABORT;
        }
        return super.validateMessage(exc, flow);
    }

    private boolean isPossibleRequestElement(javax.xml.namespace.QName name) {
        return isPossibleSOAPElement(requestElements, name);
    }

    private boolean isPossibleResponseElement(javax.xml.namespace.QName name) {
        return isPossibleSOAPElement(responseElements, name);
    }

    private boolean isPossibleSOAPElement(Set<QName> elementNames, QName name) {
        return elementNames.contains(name);
    }

    @Override
    protected List<Element> getSchemas() {
        return definitions.getSchemaElements();
    }

    /**
     * Each schema embedded in the WSDL is compiled into its own validator. A schema that
     * references a type from another embedded schema via a namespace-only {@code <xsd:import>}
     * (no {@code schemaLocation}) would otherwise fail to resolve, because the default
     * location-based resolver has no systemId to work with. This resolver serves such imports
     * from the WSDL's own embedded schemas, delegating everything else to the default resolver.
     */
    @Override
    protected LSResourceResolver getResourceResolver() {
        LSResourceResolver delegate = super.getResourceResolver();
        return (type, namespaceURI, publicId, systemId, baseURI) -> {
            if (systemId == null && namespaceURI != null) {
                var embedded = definitions.getEmbeddedSchema(namespaceURI);
                if (embedded.isPresent()) {
                    try {
                        String xsd = XMLUtil.xmlNode2String(embedded.get().getSchemaElement());
                        return new LSInputImpl(publicId, location, new ByteArrayInputStream(xsd.getBytes(UTF_8)));
                    } catch (Exception e) {
                        throw new ConfigurationException(
                                "Could not resolve embedded schema for namespace %s in WSDL at %s."
                                        .formatted(namespaceURI, location), e);
                    }
                }
            }
            return delegate.resolveResource(type, namespaceURI, publicId, systemId, baseURI);
        };
    }

    @Override
    protected Source getMessageBody(InputStream input) {
        return MessageUtil.getSOAPBody(input);
    }

    @Override
    protected void setErrorResponse(Exchange exchange, String message) {
        exchange.setResponse(createSOAPFaultResponse(Client, getErrorTitle(), Map.of("error", message)));
    }

    @Override
    protected void setErrorResponse(Exchange exchange, Interceptor.Flow flow, List<Exception> exceptions) {
        exchange.setResponse(createSOAPFaultResponse(Client, getErrorTitle(), Map.of("validation", convertExceptionsToMap(exceptions))));
    }

    @Override
    protected String getPreliminaryError(XOPReconstitutor xopr, Message msg) {
        if (isSOAP(xopr, msg))
            return null;
        return "Not a SOAP message.";
    }

    @Override
    public String getErrorTitle() {
        return "WSDL message validation failed";
    }
}