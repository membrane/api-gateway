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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class SchemaValidatorErrorHandler implements ErrorHandler {

	private static Log log = LogFactory.getLog(SchemaValidatorErrorHandler.class.getName());
	
	private Exception exception; 
	
	public void error(SAXParseException e) throws SAXException {
		exception = e;
		log.info("Error: " + e);
	}

	public void fatalError(SAXParseException e) throws SAXException {
		exception = e;
		log.info("Fatal Error: " + e);
	}

	public void warning(SAXParseException e) throws SAXException {
		log.info("Warning: " + e);
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
