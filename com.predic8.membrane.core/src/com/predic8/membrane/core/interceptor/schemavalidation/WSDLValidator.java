package com.predic8.membrane.core.interceptor.schemavalidation;

import java.io.InputStream;
import java.util.List;

import javax.xml.transform.Source;

import com.predic8.membrane.core.util.MessageUtil;
import com.predic8.schema.Schema;
import com.predic8.wsdl.WSDLParser;
import com.predic8.wsdl.WSDLParserContext;

public class WSDLValidator extends AbstractXMLValidator {

	public WSDLValidator(String location) throws Exception {
		super(location);
	}
	
	protected List<Schema> getSchemas() {
		WSDLParserContext ctx = new WSDLParserContext();
		ctx.setInput(location);
		return new WSDLParser().parse(ctx).getTypes().getSchemas();
	}
	
	protected Source getMessageBody(InputStream input) throws Exception {
		return MessageUtil.getSOAPBody(input);
	}


}
