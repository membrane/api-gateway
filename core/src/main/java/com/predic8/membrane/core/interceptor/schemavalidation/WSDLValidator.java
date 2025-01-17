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
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.multipart.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.util.SOAPUtil.*;
import com.predic8.schema.*;
import com.predic8.wsdl.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import javax.xml.namespace.*;
import javax.xml.stream.*;
import javax.xml.transform.*;
import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.Constants.SoapVersion.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.SOAPUtil.FaultCode.*;
import static com.predic8.membrane.core.util.WSDLUtil.*;
import static javax.xml.stream.XMLInputFactory.*;

public class WSDLValidator extends AbstractXMLSchemaValidator {

	static Logger log = LoggerFactory.getLogger(WSDLValidator.class.getName());

	/**
	 * A WSDL can have several definition.service elements. The serviceName is the
	 * name of the service against to validate
	 */
	private final String serviceName;

	/**
	 * List of toplevel soapElements that are valid for requests
	 */
	private Set<QName> requestElements = new HashSet<>();

	/**
	 * List of toplevel soapElements that are valid for responses
	 */
	private Set<QName> responseElements = new HashSet<>();

	/**
	 * There might be additional toplevel Elements in a schema that are not used in
	 * a WSDL message. This field controls if it is checked if an element can be used as
	 * a request or response message
	 */
	private boolean checkIfSOAPElementIsUsedAsAWSDLMessage;

	/**
	 * Does WSDL supports SOAP Version 1.1?
	 */
	private boolean soap11;

	/**
	 * Does WSDL supports SOAP Version 1.1?
	 */
	private boolean soap12;

	public WSDLValidator(ResolverMap resourceResolver, String location, String serviceName, ValidatorInterceptor.FailureHandler failureHandler, boolean skipFaults) {
		super(resourceResolver, location, failureHandler, skipFaults);
		this.serviceName = serviceName;
	}

	@Override
	public String getName() {
		return "WSDL Validator";
	}

	@Override
	public Outcome validateMessage(Exchange exc, Message msg) throws Exception {
		SOAPAnalysisResult result =  SOAPUtil.analyseSOAPMessage(xmlInputFactory, xopr, msg);

		if (result.version() == SOAP11 && !soap11) {
			exc.setResponse(createErrorResponse("SOAP version 1.1 is not valid"));
			return ABORT;
		}
		if (result.version() == SOAP12 && !soap12) {
			exc.setResponse(createErrorResponse("SOAP version 1.2 is not valid"));
			return ABORT;
		}

		if (checkIfSOAPElementIsUsedAsAWSDLMessage) {
			if (msg instanceof Request && !isPossibleRequestElement(result.soapElement())) {
				exc.setResponse(createErrorResponse("%s is not a valid request element. Possible elements are %s".formatted(result.soapElement(), requestElements)));
				return ABORT;
			}
			if (msg instanceof Response && !isPossibleResponseElement(result.soapElement())) {
				exc.setResponse(createErrorResponse("%s is not a valid response element. Possible elements are %s".formatted(result.soapElement(), requestElements)));
				return ABORT;
			}
		}
		return super.validateMessage(exc, msg);
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
	protected List<Schema> getSchemas() {
		WSDLParserContext ctx = new WSDLParserContext();
		ctx.setInput(location);

		Definitions definitions = parseWsdl(ctx);
		readPossibleToplevelSOAPElements(definitions);
		return getSchemas(definitions);
	}

	private Definitions parseWsdl(WSDLParserContext ctx) {
		Definitions definitions;
		try {
			definitions = getWsdlParser().parse(ctx);
		}
		catch (NoSuchElementException e) {
			log.error(e.getMessage());
			throw new RuntimeException();
		}
		catch (RuntimeException e) {
			if (e.getCause() instanceof ResourceRetrievalException re) {
				String msg = "Could not read WSDL from %s or its dependent XML Schemas.".formatted(location);
				log.error(msg);
				throw new IllegalStateException(msg, re);
			}
			log.error("Error downloading WSDL from {}.", location);
			throw e;
		}
		return definitions;
	}

	private static @NotNull List<Schema> getSchemas(Definitions definitions) {
		return definitions.getTypes().stream().map(Types::getSchemas).flatMap(List::stream).toList();
	}

	private void readPossibleToplevelSOAPElements(Definitions definitions) {

		if (serviceName != null) {
			checkIfSOAPElementIsUsedAsAWSDLMessage = true;
			Service service = WSDLUtil.getService(definitions, serviceName);
			determinePossibleSoapVersions(service);
			requestElements = getPossibleSOAPElements(service, Direction.REQUEST);
			responseElements = getPossibleSOAPElements(service, Direction.RESPONSE);
			return;
		}

		// WSDL without a service element
		if (definitions.getServices().isEmpty()) {
			checkIfSOAPElementIsUsedAsAWSDLMessage = true;
			// No binding information so allow all SOAP versions
			soap11 = true;
			soap12 = true;
			definitions.getPortTypes().forEach(portType -> {
				requestElements.addAll(getPossibleSOAPElements(portType, Direction.REQUEST));
				responseElements.addAll(getPossibleSOAPElements(portType, Direction.RESPONSE));
			});
			return;
		}

		// Check what SOAP versions are declared in the WSDL
		definitions.getServices().forEach(this::determinePossibleSoapVersions);
	}

	private void determinePossibleSoapVersions(Service service) {
		service.getPorts().forEach(port -> {
			switch (getSOAPVersion(port)) {
				case SOAP11: soap11 = true; break;
				case SOAP12: soap12 = true; break;
			}
		});
	}

	private @NotNull WSDLParser getWsdlParser() {
		WSDLParser parser = new WSDLParser();
		parser.setResourceResolver(resolver.toExternalResolver().toExternalResolver());
		return parser;
	}

	@Override
	protected Source getMessageBody(InputStream input) throws Exception {
		return MessageUtil.getSOAPBody(input);
	}

	@Override
	protected Response createErrorResponse(String message) {
		return SOAPUtil.createSOAPValidationErrorResponse(Client, "Message validation failed!",message);
	}

	private static final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
	static {
		xmlInputFactory.setProperty(IS_REPLACING_ENTITY_REFERENCES, false);
		xmlInputFactory.setProperty(IS_SUPPORTING_EXTERNAL_ENTITIES, false);
	}

	@Override
	protected boolean isFault(Message msg) {
		return SOAPUtil.analyseSOAPMessage(xmlInputFactory, xopr, msg).isFault();
	}

	@Override
	protected String getPreliminaryError(XOPReconstitutor xopr, Message msg) {
		if (SOAPUtil.isSOAP(xmlInputFactory, xopr, msg))
			return null;
		return "Not a SOAP message.";
	}
}
