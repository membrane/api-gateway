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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.multipart.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.schema.Schema;
import org.apache.commons.text.*;
import org.slf4j.*;

import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.validation.*;
import java.io.*;
import java.util.*;

import static java.nio.charset.StandardCharsets.*;

public class XMLSchemaValidator extends AbstractXMLSchemaValidator {
	private static final Logger log = LoggerFactory.getLogger(XMLSchemaValidator.class.getName());

	public XMLSchemaValidator(ResolverMap resourceResolver, String location, ValidatorInterceptor.FailureHandler failureHandler) throws Exception {
		super(resourceResolver, location, failureHandler);
	}

	@Override
	protected List<Schema> getSchemas() {
		return null; // never gets called
	}

	@Override
	protected List<Validator> createValidators() throws Exception {
		SchemaFactory sf = SchemaFactory.newInstance(Constants.XSD_NS);
		sf.setResourceResolver(resourceResolver.toLSResourceResolver());
		List<Validator> validators = new ArrayList<>();
		log.debug("Creating validator for schema: " + location);
		StreamSource ss = new StreamSource(resourceResolver.resolve(location));
		ss.setSystemId(location);
		Validator validator = sf.newSchema(ss).newValidator();
		validator.setResourceResolver(resourceResolver.toLSResourceResolver());
		validator.setErrorHandler(new SchemaValidatorErrorHandler());
		validators.add(validator);
		return validators;
	}

	@Override
	protected Source getMessageBody(InputStream input) {
		return new StreamSource(input);
	}

	@Override
	protected Response createErrorResponse(String message) {
		return Response.
				badRequest().
				contentType(MimeType.TEXT_XML_UTF8).
				body(("<error>" + StringEscapeUtils.escapeXml11(message) + "</error>").getBytes(UTF_8)).
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
