package com.predic8.membrane.core.interceptor.schemavalidation;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.util.ResourceResolver;
import com.predic8.schema.Schema;

public class XMLSchemaValidator extends AbstractXMLSchemaValidator {
	private static Log log = LogFactory.getLog(XMLSchemaValidator.class.getName());

	public XMLSchemaValidator(ResourceResolver resourceResolver, String location, ValidatorInterceptor.FailureHandler failureHandler) throws Exception {
		super(resourceResolver, location, failureHandler);
	}
	
	protected List<Schema> getSchemas() {
		return null; // never gets called
	}
	
	@Override
	protected List<Validator> createValidators() throws Exception {
		SchemaFactory sf = SchemaFactory.newInstance(Constants.XSD_NS);
		sf.setResourceResolver(resourceResolver.toLSResourceResolver());
		List<Validator> validators = new ArrayList<Validator>();
		log.info("Creating validator for schema: " + location);
		StreamSource ss = new StreamSource(resourceResolver.resolve(location));
		ss.setSystemId(location);
		Validator validator = sf.newSchema(ss).newValidator();
		validator.setResourceResolver(resourceResolver.toLSResourceResolver());
		validator.setErrorHandler(new SchemaValidatorErrorHandler());
		validators.add(validator);
		return validators;
	}

	protected Source getMessageBody(InputStream input) throws Exception {
		return new StreamSource(input);
	}
	
	@Override
	protected Response createErrorResponse(String message) {
		return Response.
				badRequest().
				contentType(MimeType.TEXT_XML_UTF8).
				body(("<error>" + StringEscapeUtils.escapeXml(message) + "</error>").getBytes(Constants.UTF_8_CHARSET)).
				build();
	}


}
