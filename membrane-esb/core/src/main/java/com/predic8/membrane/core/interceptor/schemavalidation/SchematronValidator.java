package com.predic8.membrane.core.interceptor.schemavalidation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
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
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.util.ResourceResolver;

public class SchematronValidator implements IValidator {

	private static final Charset UTF8 = Charset.forName("UTF-8");
	private final ArrayBlockingQueue<Transformer> transformers;
	private final XMLInputFactory xmlInputFactory;
	private final ValidatorInterceptor.FailureHandler failureHandler;
	private final XOPReconstitutor xopr = new XOPReconstitutor();
	
	private final AtomicLong valid = new AtomicLong();
	private final AtomicLong invalid = new AtomicLong();
	
	
	public SchematronValidator(ResourceResolver resourceResolver, String schematron, ValidatorInterceptor.FailureHandler failureHandler, Router router) throws Exception {
		this.failureHandler = failureHandler;
		
		//works as standalone "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl"
		TransformerFactory fac;
		try {
			fac = router.getBean("transformerFactory", TransformerFactory.class);
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
		t.transform(new StreamSource(router.getResourceResolver().resolve(schematron)), r);
		
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
	public Outcome validateMessage(Exchange exc, Message msg) throws Exception {
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
						setErrorMessage(exc, new String(result, UTF8), false);
						invalid.incrementAndGet();
						return Outcome.ABORT;
					}
				}
			}

		} catch (TransformerException e) {
			setErrorMessage(exc, e.getMessage(), true);
			invalid.incrementAndGet();
			return Outcome.ABORT;
		} catch (Exception e) {
			e.printStackTrace();
			setErrorMessage(exc, "internal error", true);
			invalid.incrementAndGet();
			return Outcome.ABORT;
		}
		valid.incrementAndGet();
		return Outcome.CONTINUE;
	}
	
	private void setErrorMessage(Exchange exc, String message, boolean escape) {
		String MSG_HEADER = "<?xml version=\"1.0\"?>\r\n<error>";
		String MSG_FOOTER = "</error>";
		if (escape)
			message = MSG_HEADER + StringEscapeUtils.escapeXml(message) + MSG_FOOTER;

		if (failureHandler != null) {
			failureHandler.handleFailure(message, exc);
			exc.setResponse(Response.badRequest().contentType("text/xml;charset=utf-8").body((MSG_HEADER + MSG_FOOTER).getBytes()).build());
		} else {
			exc.setResponse(Response.badRequest().contentType("text/xml;charset=utf-8").body(message.getBytes(UTF8)).build());
		}
	}
	
	@Override
	public long getValid() {
		return valid.get();
	}

	@Override
	public long getInvalid() {
		return invalid.get();
	}

	private final class NullErrorListener implements ErrorListener {
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
