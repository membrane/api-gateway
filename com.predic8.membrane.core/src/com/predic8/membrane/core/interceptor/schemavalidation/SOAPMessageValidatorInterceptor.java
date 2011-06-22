package com.predic8.membrane.core.interceptor.schemavalidation;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.HttpUtil;
import com.predic8.schema.Schema;
import com.predic8.wsdl.WSDLParser;
import com.predic8.wsdl.WSDLParserContext;

public class SOAPMessageValidatorInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(SOAPMessageValidatorInterceptor.class.getName());
		
	private List<Validator> validators;
	
	private String wsdl;
	
	public void init() throws Exception {
		validators = getValidators();
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		return validateMessage(exc, exc.getRequest());
	}
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		return validateMessage(exc, exc.getResponse());
	}
	
	private Outcome validateMessage(Exchange exc, Message msg) throws Exception {
		List<Exception> exceptions = new ArrayList<Exception>();
		for (Validator validator: validators) {
			validator.validate(getSOAPBody(msg.getBodyAsStream()));
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
	
	private String getErrorMsg(List<Exception> excs) {
		StringBuffer buf = new StringBuffer();
		buf.append("Validation failed: ");
		for (Exception e : excs) {
			buf.append(e);
			buf.append("; ");
		}
		return buf.toString();
	}
	
	private List<Validator> getValidators() throws Exception {
		log.info("Get validators for WSDL: " + wsdl);
		WSDLParserContext ctx = new WSDLParserContext();
		ctx.setInput(wsdl);
		SchemaFactory sf = SchemaFactory.newInstance(Constants.XSD_NS);
		sf.setResourceResolver(new SOAModelResourceResolver(wsdl));
		List<Validator> validators = new ArrayList<Validator>();
		for (Schema schema : getEmbeddedSchemas(ctx)) {
			log.info("Adding embedded schema: " + schema);
			Validator validator = sf.newSchema(new StreamSource(new ByteArrayInputStream(schema.getAsString().getBytes()))).newValidator();
			validator.setErrorHandler(new SchemaValidatorErrorHandler());
			validators.add(validator);
		}
		return validators;
	}
	
	private List<Schema> getEmbeddedSchemas(WSDLParserContext ctx) {
		return new WSDLParser().parse(ctx).getTypes().getSchemas();
	}
	
	private static Source getSOAPBody(InputStream stream) throws Exception {
		return new SAXSource(new SOAPXMLFilter(XMLReaderFactory.createXMLReader()), new InputSource(stream));
	}
	
	public void setWsdl(String wsdl) {
		this.wsdl = wsdl;
	}
	
	public String getWsdl() {
		return wsdl;
	}
}
