package com.predic8.membrane.core.interceptor.schemavalidation;

import java.io.InputStream;
import java.util.List;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;

import org.xml.sax.InputSource;

import com.predic8.schema.Schema;
import com.predic8.schema.SchemaParser;

public class XMLSchemaValidator extends AbstractXMLValidator {
	
	public XMLSchemaValidator(String location) throws Exception {
		super(location);
	}
	
	@SuppressWarnings("unchecked")
	protected List<Schema> getSchemas() {
		return (List<Schema>) new SchemaParser().parse(location).getAllSchemas();
	}

	protected Source getMessageBody(InputStream input) throws Exception {
		return new SAXSource(new InputSource(input));
	}

}
