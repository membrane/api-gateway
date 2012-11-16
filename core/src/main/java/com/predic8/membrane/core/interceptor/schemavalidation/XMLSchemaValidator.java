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
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
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
		log.debug("Creating validator for schema: " + location);
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

	@Override
	protected boolean isFault(Message msg) {
		return false;
	}
	
	@Override
	protected String getPreliminaryError(XOPReconstitutor xopr, Message msg) {
		return null;
	}
}
