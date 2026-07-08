/* Copyright 2011 predic8 GmbH, www.predic8.com

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

public class SchemaValidatorErrorHandler implements ErrorHandler {

	private static final Logger log = LoggerFactory.getLogger(SchemaValidatorErrorHandler.class.getName());

	private Exception exception;

	// Errors are collected here and only reported by AbstractXMLSchemaValidator once the message
	// has failed *all* embedded schemas. Logging here would emit spurious "Error:" lines for a
	// perfectly valid message that simply matches a different embedded schema of the same WSDL.
	public void error(SAXParseException e) {
		exception = e;
	}

	public void fatalError(SAXParseException e) {
		exception = e;
	}

	public void warning(SAXParseException e) {
		log.debug("Warning: {}", e.getMessage());
	}

	public Exception getException() {
		return exception;
	}

	public boolean noErrors() {
		return exception == null;
	}

	public void reset() {
		exception = null;
	}
}
