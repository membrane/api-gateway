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

import com.predic8.membrane.annot.Constants.SoapVersion;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.LSInputImpl;
import com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion;
import com.predic8.membrane.core.util.xml.XMLUtil;
import com.predic8.membrane.core.util.xml.parser.HardenedSchemaFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

import static com.predic8.membrane.annot.Constants.SoapVersion.SOAP11;
import static com.predic8.membrane.annot.Constants.SoapVersion.SOAP12;
import static com.predic8.membrane.annot.Constants.XSD_NS;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.schemavalidation.WSDLMessageElementExtractor.*;
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
     * Elements allowed as the payload of a SOAP fault's detail/Detail, as declared via
     * wsdl:fault on the service's operations.
     */
    private final Set<QName> faultDetailElements;

    /**
     * Schemas describing the structural shape of a SOAP 1.1/1.2 Fault element (faultcode/
     * faultstring/detail resp. Code/Reason/Detail). Membrane doesn't otherwise bundle a SOAP
     * envelope schema, so these are compiled once per validator instance from small dedicated
     * resources rather than being derived from the WSDL.
     */
    private final Map<SoapVersion, Schema> faultStructureSchemas;

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
                    """.formatted(location, e.getMessage()), e);
        }

        requestElements = getPossibleRequestElements(definitions, serviceName);
        responseElements = getPossibleResponseElements(definitions, serviceName);
        faultDetailElements = getPossibleFaultDetailElements(definitions, serviceName);
        versions = definitions.getSoapVersions();

        faultStructureSchemas = new EnumMap<>(SoapVersion.class);
        faultStructureSchemas.put(SOAP11, compileFaultStructureSchema("soap11-fault.xsd"));
        faultStructureSchemas.put(SOAP12, compileFaultStructureSchema("soap12-fault.xsd"));
    }

    private static Schema compileFaultStructureSchema(String resourceName) {
        try {
            var sf = HardenedSchemaFactory.newInstance(XSD_NS);
            return sf.newSchema(new StreamSource(
                    Objects.requireNonNull(WSDLValidator.class.getResourceAsStream(resourceName),
                            "Bundled schema resource not found: " + resourceName)));
        } catch (SAXException e) {
            throw new ConfigurationException("Cannot load bundled SOAP fault schema %s.".formatted(resourceName), e);
        }
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
            return abort(exc, flow, "Not a valid SOAP message.");
        }

        // wsdl:fault describes what a service returns, so a fault is never a legal request - not
        // even with skipFaults, which is about tolerating the faults a backend sends back.
        if (result.isFault() && flow == Interceptor.Flow.REQUEST) {
            return abort(exc, flow, "A SOAP Fault is not a valid request. Possible elements are %s".formatted(requestElements));
        }

        if (result.isFault() && skipFaults) {
            log.debug("Skipping validation of fault message.");
            return CONTINUE;
        }

        if (!versions.isEmpty()) {
            if (result.version() == SOAP11 && !versions.contains(SOAP_11)) {
                return abort(exc, flow, "SOAP version 1.1 is not valid");
            }
            if (result.version() == SOAP12 && !versions.contains(SOAP_12)) {
                return abort(exc, flow, "SOAP version 1.2 is not valid");
            }
        }

        if (result.isFault()) {
            return validateFault(exc, message, result.version());
        }

        if (flow == Interceptor.Flow.REQUEST && !isPossibleRequestElement(result.soapElement())) {
            return abort(exc, flow, "%s is not a valid request element. Possible elements are %s".formatted(result.soapElement(), requestElements));
        }
        if (flow == Interceptor.Flow.RESPONSE && !isPossibleResponseElement(result.soapElement())) {
            return abort(exc, flow, "%s is not a valid response element. Possible elements are %s".formatted(result.soapElement(), responseElements));
        }
        return super.validateMessage(exc, flow);
    }

    /**
     * Validates a SOAP fault: first structurally, against the bundled SOAP 1.1/1.2 Fault shape
     * (faultcode/faultstring/detail resp. Code/Reason/Detail), then - if a detail/Detail payload
     * is present and the WSDL declares wsdl:fault elements for this service - validates that
     * payload against the WSDL's embedded schemas, same as request/response elements.
     * <p>
     * Only reached in the response flow: a fault is rejected outright as a request, so every
     * rejection here is reported against {@link Interceptor.Flow#RESPONSE}.
     */
    private Outcome validateFault(Exchange exc, Message message, SoapVersion version) throws Exception {
        var exceptions = new ArrayList<Exception>();

        // Only SOAP 1.1 and 1.2 fault shapes are bundled. analyseSOAPMessage cannot currently
        // report any other version for a message it accepted as SOAP, so this is a guard against
        // an invariant three classes away - not a reachable state.
        var faultStructureSchema = faultStructureSchemas.get(version);
        if (faultStructureSchema == null) {
            return abort(exc, Interceptor.Flow.RESPONSE, "Cannot validate a fault of SOAP version %s. Only SOAP 1.1 and SOAP 1.2 faults are supported.".formatted(version));
        }

        // A fresh validator per fault, deliberately not from the validators pool: the expensive
        // artifact is the compiled Schema, and that is already cached in faultStructureSchemas.
        // Schema is immutable and thread-safe, so newValidator() is a cheap allocation off an
        // already-compiled grammar - and a fresh instance has no handler state to reset. These
        // schemas must not join the validators pool: that pool holds the WSDL's embedded schemas,
        // which validateAgainstSchemas iterates looking for *any* match, whereas exactly one fault
        // schema applies, selected by SOAP version.
        var structureValidator = faultStructureSchema.newValidator();
        HardenedSchemaFactory.hardenValidator(structureValidator);
        var handler = new SchemaValidatorErrorHandler();
        structureValidator.setErrorHandler(handler);
        structureValidator.validate(getMessageBody(xopr.reconstituteIfNecessary(message)));
        if (!handler.noErrors()) {
            return abort(exc, Interceptor.Flow.RESPONSE, List.of(handler.getException()));
        }

        var detailEntries = extractFaultDetailElements(xopr, message, version);
        if (detailEntries.isEmpty()) {
            // detail/Detail is optional per the SOAP spec, and may be empty.
            return CONTINUE;
        }
        if (faultDetailElements.isEmpty()) {
            log.debug("WSDL declares no wsdl:fault for this service; skipping validation of fault detail content.");
            return CONTINUE;
        }
        // SOAP allows several detail entries, but a WSDL fault message has a single part, so a
        // WSDL-described fault carries exactly one - and only that one could be validated below.
        if (detailEntries.size() > 1) {
            return abort(exc, Interceptor.Flow.RESPONSE, "A fault detail must contain exactly one element, because a WSDL fault message has a single part, but found %d: %s".formatted(detailEntries.size(), detailEntries));
        }
        var detailElement = detailEntries.getFirst();
        if (!faultDetailElements.contains(detailElement)) {
            return abort(exc, Interceptor.Flow.RESPONSE, "%s is not a valid fault detail element. Possible elements are %s".formatted(detailElement, faultDetailElements));
        }

        if (!validateAgainstSchemas(() -> getFaultDetailBody(xopr.reconstituteIfNecessary(message), version), exceptions)) {
            return abort(exc, Interceptor.Flow.RESPONSE, exceptions);
        }
        return CONTINUE;
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
        return getSOAPBody(input);
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