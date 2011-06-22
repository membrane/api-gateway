package com.predic8.membrane.core.interceptor.schemavalidation;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import com.predic8.schema.Schema;
import com.predic8.wsdl.WSDLParser;
import com.predic8.wsdl.WSDLParserContext;

public class SOAModelResourceResolver implements LSResourceResolver {

	private static Log log = LogFactory.getLog(SOAModelResourceResolver.class.getName());
	
	private List<Schema> schemas;
	
	public SOAModelResourceResolver(String wsdl) {
		WSDLParserContext ctx = new WSDLParserContext();
		ctx.setInput(wsdl);
		schemas = getAllSchemas(ctx);
	}

	private List<Schema> getAllSchemas(WSDLParserContext ctx) {
		return new WSDLParser().parse(ctx).getTypes().getAllSchemas();
	}
	
	@Override
	public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
		for (Schema schema : schemas) {
			if (namespaceURI.equals(schema.getTargetNamespace()))
				return new LSInputImpl(publicId, systemId, schema.getAsString());
		}
		log.error("No matching schema found for target namespace: " + namespaceURI);
		return null;
	}
}
