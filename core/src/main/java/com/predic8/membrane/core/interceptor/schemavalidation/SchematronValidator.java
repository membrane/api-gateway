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

import static com.predic8.membrane.core.Constants.UTF_8_CHARSET;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.lang.StringEscapeUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.resolver.ResolverMap;

public class SchematronValidator implements IValidator {

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
		fac.setURIResolver(new URIResolver() {
			@Override
			public Source resolve(String href, String base) throws TransformerException {
				return new StreamSource(SchematronValidator.class.getResourceAsStream(href));
			}
		});
		Transformer t = fac.newTransformer(new StreamSource(SchematronValidator.class.getResourceAsStream("conformance1-5.xsl")));

		// transform schematron-XML into XSLT
		DOMResult r = new DOMResult();
		t.transform(new StreamSource(router.getResolverMap().resolve(schematron)), r);
		
		// build XSLT transformers
		fac.setURIResolver(null);
		int concurrency = Runtime.getRuntime().availableProcessors() * 2;
		transformers = new ArrayBlockingQueue<Transformer>(concurrency);
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
	public Outcome validateMessage(Exchange exc, Message msg, String source) throws Exception {
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
						setErrorMessage(exc, new String(result, UTF_8_CHARSET), false, source);
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
			e.printStackTrace();
			setErrorMessage(exc, "internal error", true, source);
			invalid.incrementAndGet();
			return Outcome.ABORT;
		}
		valid.incrementAndGet();
		return Outcome.CONTINUE;
	}
	
	private void setErrorMessage(Exchange exc, String message, boolean escape, String source) {
		String MSG_HEADER = "<?xml version=\"1.0\"?>\r\n<error" + (escape ? " source=\"" + StringEscapeUtils.escapeXml(source) + "\"" : "") + ">";
		String MSG_FOOTER = "</error>";
		if (escape)
			message = MSG_HEADER + StringEscapeUtils.escapeXml(message) + MSG_FOOTER;

		if (failureHandler != null) {
			failureHandler.handleFailure(message, exc);
			exc.setResponse(Response.badRequest().contentType(MimeType.TEXT_XML_UTF8).body((MSG_HEADER + MSG_FOOTER).getBytes(UTF_8_CHARSET)).build());
		} else {
			exc.setResponse(Response.badRequest().contentType(MimeType.TEXT_XML_UTF8).body(message.getBytes(UTF_8_CHARSET)).build());
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
		public void warning(TransformerException exception)
				throws TransformerException {
		}

		@Override
		public void fatalError(TransformerException exception)
				throws TransformerException {
		}

		@Override
		public void error(TransformerException exception)
				throws TransformerException {
		}
	}

}
