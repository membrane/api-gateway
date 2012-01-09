package com.predic8.membrane.core.interceptor.schemavalidation;

import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.XMLReaderFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.HttpUtil;
import com.predic8.schema.Schema;

public class XMLValidator implements IValidator {
	private static Log log = LogFactory.getLog(XMLValidator.class.getName());
	public enum Type { WSDL, XSD }

	private List<Validator> validators;
	private Type type;
	private String location;

	
	/**
	 * @param location the location of the WSDL, if type == WSDL, or the location of the XSD, if type == XSD.
	 */
	public XMLValidator(Type type, String location) throws Exception {
		this.type = type;
		this.location = location;
		validators = getValidators();
	}
	
	public Outcome validateMessage(Exchange exc, Message msg) throws Exception {
		List<Exception> exceptions = new ArrayList<Exception>();
		for (Validator validator: validators) {
			validator.validate(getMessageBody(msg.getBodyAsStream()));
			SchemaValidatorErrorHandler handler = (SchemaValidatorErrorHandler)validator.getErrorHandler();
			// the message must be valid for one schema embedded into WSDL 
			if (handler.noErrors()) {
				return Outcome.CONTINUE;
			}
			exceptions.add(handler.getException());
			handler.reset();
		}
		exc.setResponse(HttpUtil.createSOAPFaultResponse(getErrorMsg(exceptions)));
		return Outcome.ABORT;
	}
	
	private List<Validator> getValidators() throws Exception {
		SchemaFactory sf = SchemaFactory.newInstance(Constants.XSD_NS);
		SOAModelResourceResolver resolver = getResolver();
		sf.setResourceResolver(resolver);
		
		List<Validator> validators = new ArrayList<Validator>();
		for (Schema schema : resolver.schemas) {
			log.info("Creating validator for schema: " + schema);
			Validator validator = sf.newSchema(new StreamSource(new StringReader(schema.getAsString()))).newValidator();
			validator.setErrorHandler(new SchemaValidatorErrorHandler());
			validators.add(validator);
		}
		return validators;
	}
	
	private SOAModelResourceResolver getResolver() {
		SOAModelResourceResolver resolver = new SOAModelResourceResolver();
		switch (type) {
		case XSD:
			resolver.loadFromSchema(location);
			break;
		case WSDL:
			resolver.loadFromWSDL(location);
			break;
		}
		return resolver;
	}
	
	private static Source getSOAPBody(InputStream stream) throws Exception {
		return new SAXSource(new SOAPXMLFilter(XMLReaderFactory.createXMLReader()), new InputSource(stream));
	}

	private Source getMessageBody(InputStream input) throws Exception {
		if (type == Type.XSD)
			return new SAXSource(new InputSource(input));
		return getSOAPBody(input);
	}

	private String getErrorMsg(List<Exception> excs) {
		StringBuffer buf = new StringBuffer();
		buf.append("Validation failed: ");
		for (Exception e : excs) {
			buf.append(e);
			buf.append("; ");
		}
		return buf.toString();
	}

}
