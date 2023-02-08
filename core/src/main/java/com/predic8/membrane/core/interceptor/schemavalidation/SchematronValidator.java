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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.multipart.*;
import com.predic8.membrane.core.resolver.*;
import org.apache.commons.text.*;
import org.slf4j.*;
import org.springframework.beans.factory.*;

import javax.xml.stream.*;
import javax.xml.stream.events.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.nio.charset.StandardCharsets.*;

public class SchematronValidator implements IValidator {
	private static final Logger log = LoggerFactory.getLogger(SchematronValidator.class.getName());

	private final ArrayBlockingQueue<Transformer> transformers;
	private final XMLInputFactory xmlInputFactory;
	private final ValidatorInterceptor.FailureHandler failureHandler;
	private final XOPReconstitutor xopr = new XOPReconstitutor();

	private final AtomicLong valid = new AtomicLong();
	private final AtomicLong invalid = new AtomicLong();


	public SchematronValidator(ResolverMap resourceResolver, String schematron, ValidatorInterceptor.FailureHandler failureHandler, Router router, BeanFactory beanFactory) throws Exception {
		this.failureHandler = failureHandler;

		//works as standalone "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl"
		TransformerFactory fac;
		try {
			fac = beanFactory.getBean("transformerFactory", TransformerFactory.class);
		} catch (NoSuchBeanDefinitionException e) {
			throw new RuntimeException("Please define a bean called 'transformerFactory' in monitor-beans.xml, e.g. with " +
					"<spring:bean id=\"transformerFactory\" class=\"com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl\" />", e);
		}
		fac.setURIResolver((href, base) -> new StreamSource(SchematronValidator.class.getResourceAsStream(href)));
		Transformer t = fac.newTransformer(new StreamSource(SchematronValidator.class.getResourceAsStream("conformance1-5.xsl")));

		// transform schematron-XML into XSLT
		DOMResult r = new DOMResult();
		t.transform(new StreamSource(router.getResolverMap().resolve(schematron)), r);

		// build XSLT transformers
		fac.setURIResolver(null);
		int concurrency = Runtime.getRuntime().availableProcessors() * 2;
		transformers = new ArrayBlockingQueue<>(concurrency);
		for (int i = 0; i < concurrency; i++) {
			Transformer transformer = fac.newTransformer(new DOMSource(r.getNode()));
			transformer.setErrorListener(new NullErrorListener()); // silence console logging
			transformers.put(transformer);
		}

		xmlInputFactory = XMLInputFactory.newInstance();
		xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
	}

	@Override
	public Outcome validateMessage(Exchange exc, Message msg, String source) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		try {
			Transformer transformer = transformers.take();
			try {
				transformer.transform(new StreamSource(xopr.reconstituteIfNecessary(msg)), new StreamResult(baos));
			} finally {
				transformers.put(transformer);
			}

			byte[] result = baos.toByteArray();

			// check for errors
			XMLEventReader parser;
			synchronized (xmlInputFactory) {
				parser = xmlInputFactory.createXMLEventReader(new ByteArrayInputStream(result));
			}
			while (parser.hasNext()) {
				XMLEvent event = parser.nextEvent();
				if (event.isStartElement()) {
					StartElement startElement = (StartElement)event;
					if (startElement.getName().getLocalPart().equals("failed-assert")) {
						setErrorMessage(exc, new String(result, UTF_8), false, source);
						invalid.incrementAndGet();
						return Outcome.ABORT;
					}
				}
			}

		} catch (TransformerException e) {
			setErrorMessage(exc, e.getMessage(), true, source);
			invalid.incrementAndGet();
			return Outcome.ABORT;
		} catch (Exception e) {
			log.error("", e);
			setErrorMessage(exc, "internal error", true, source);
			invalid.incrementAndGet();
			return Outcome.ABORT;
		}
		valid.incrementAndGet();
		return Outcome.CONTINUE;
	}

	private void setErrorMessage(Exchange exc, String message, boolean escape, String source) {
		String MSG_HEADER = "<?xml version=\"1.0\"?>\r\n<error" + (escape ? " source=\"" + StringEscapeUtils.escapeXml11(source) + "\"" : "") + ">";
		String MSG_FOOTER = "</error>";
		if (escape)
			message = MSG_HEADER + StringEscapeUtils.escapeXml11(message) + MSG_FOOTER;

		if (failureHandler != null) {
			failureHandler.handleFailure(message, exc);
			exc.setResponse(Response.badRequest().contentType(MimeType.TEXT_XML_UTF8).body((MSG_HEADER + MSG_FOOTER).getBytes(UTF_8)).build());
		} else {
			exc.setResponse(Response.badRequest().contentType(MimeType.TEXT_XML_UTF8).body(message.getBytes(UTF_8)).build());
		}
		if (!escape)
			exc.getResponse().getHeader().add(Header.VALIDATION_ERROR_SOURCE, source);
	}

	@Override
	public long getValid() {
		return valid.get();
	}

	@Override
	public long getInvalid() {
		return invalid.get();
	}

	private static final class NullErrorListener implements ErrorListener {
		@Override
		public void warning(TransformerException exception) {
		}

		@Override
		public void fatalError(TransformerException exception) {
		}

		@Override
		public void error(TransformerException exception) {
		}
	}
}