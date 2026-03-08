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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.multipart.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.util.wsdl.parser.Definitions.*;
import com.predic8.wsdl.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import javax.xml.namespace.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.Constants.SoapVersion.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.SOAPUtil.FaultCode.*;
import static com.predic8.membrane.core.util.SOAPUtil.*;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.*;

public class WSDLValidator extends AbstractXMLSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(WSDLValidator.class.getName());

    /**
     * A WSDL can have several definition.service elements. The serviceName is the
     * name of the service against to validate
     */
    private final String serviceName;

    /**
     * List of toplevel soapElements that are valid for requests
     */
    private Set<QName> requestElements;

    /**
     * List of toplevel soapElements that are valid for responses
     */
    private Set<QName> responseElements;

    /**
     * There might be additional toplevel Elements in a schema that are not used in
     * a WSDL message. This field controls if it is checked if an element can be used as
     * a request or response message
     */
    private boolean checkIfSOAPElementIsUsedAsAWSDLMessage;

    private Set<SOAPVersion> versions;

    private final boolean skipFaults;

    /**
     * Parsed WSDL document
     */
    private com.predic8.membrane.core.util.wsdl.parser.Definitions definitions;

    public WSDLValidator(ResolverMap resourceResolver, String location, String serviceName, ValidatorInterceptor.FailureHandler failureHandler, boolean skipFaults) {
        super(resourceResolver, location, failureHandler);
        this.skipFaults = skipFaults;
        this.serviceName = serviceName;

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

        requestElements = WSDLMessageElementExtractor.getPossibleRequestElements(definitions, serviceName);
        responseElements = WSDLMessageElementExtractor.getPossibleResponseElements(definitions, serviceName);

        versions = definitions.getSoapVersions();
    }

    @Override
    public String getName() {
        return "wsdl-validator";
    }

    @Override
    public Outcome validateMessage(Exchange exc, Interceptor.Flow flow) throws Exception {
        var msg = exc.getMessage(flow);
        var result = analyseSOAPMessage(xopr, msg);

        if (!result.isSOAP()) {
            setErrorResponse(exc, "Not a valid SOAP message.");
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

//        if (checkIfSOAPElementIsUsedAsAWSDLMessage) {
        if (msg instanceof Request && !isPossibleRequestElement(result.soapElement())) {
            setErrorResponse(exc, "%s is not a valid request element. Possible elements are %s".formatted(result.soapElement(), requestElements));
            return ABORT;
        }
        if (msg instanceof Response && !isPossibleResponseElement(result.soapElement())) {
            setErrorResponse(exc, "%s is not a valid response element. Possible elements are %s".formatted(result.soapElement(), responseElements));
            return ABORT;
        }
//        }

        return super.validateMessage(exc, flow);
    }

    private boolean isPossibleRequestElement(javax.xml.namespace.QName name) {
        return isPossibleSOAPElement(requestElements, name);
    }

    private boolean isPossibleResponseElement(javax.xml.namespace.QName name) {
        return isPossibleSOAPElement(responseElements, name);
    }

    private boolean isPossibleSOAPElement(Set<QName> elementNames, QName name) {
        return elementNames.stream().anyMatch(qn -> qn.equals(name));
    }

    @Override
    protected List<Element> getSchemas() {
        return definitions.getSchemaElements();
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