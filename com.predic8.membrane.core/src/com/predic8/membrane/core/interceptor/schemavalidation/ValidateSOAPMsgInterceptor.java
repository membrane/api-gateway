package com.predic8.membrane.core.interceptor.schemavalidation;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.InputSource;
import org.xml.sax.helpers.XMLReaderFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.HttpUtil;
import com.predic8.schema.Schema;
import com.predic8.wsdl.Definitions;
import com.predic8.wsdl.Types;
import com.predic8.wsdl.WSDLParser;
import com.predic8.wsdl.WSDLParserContext;

public class ValidateSOAPMsgInterceptor extends AbstractInterceptor {
	
	private String wsdl;

	private TestErrorHandler handler;

	private List<Schema> schemas;
	
	public void init() {
		handler = new TestErrorHandler();
		schemas = getSchemas();
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
			
		for (Schema predicschema : schemas) {
			
			SchemaFactory sf = SchemaFactory.newInstance(Constants.XSD_NS);
			
			javax.xml.validation.Schema schema = sf.newSchema(new StreamSource(new ByteArrayInputStream(predicschema.getAsString().getBytes())));

			Validator validator = schema.newValidator();

			validator.setErrorHandler(handler);
			
			Source xmlSource = getSAXSource(exc.getRequest().getBodyAsStream()); 
			
			validator.validate(xmlSource);
			
			if (handler.getException() == null)
				return Outcome.CONTINUE;
			
			handler.reset();
		}
		
		Response resp = HttpUtil.createSOAPFaultResponse("Validation failed.");
		exc.setResponse(resp);
		
		return Outcome.ABORT;
	}

	public List<Schema> getSchemas() {
		WSDLParserContext ctx = new WSDLParserContext();
		ctx.setInput(wsdl);
		Definitions defs = new WSDLParser().parse(ctx);

		Types tps = defs.getTypes();
		return tps.getAllSchemas();
	}

	public String getWsdl() {
		return wsdl;
	}

	public void setWsdl(String wsdl) {
		this.wsdl = wsdl;
	}

	private static SAXSource getSAXSource(InputStream stream) throws Exception {
		return new SAXSource(new SOAPXMLFilter(XMLReaderFactory.createXMLReader()), new InputSource(stream));
	}
	
}
