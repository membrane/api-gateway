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

package com.predic8.membrane.osgi.extender.logger;

import org.apache.log4j.Appender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Log4JToSLF4J implements Appender {
	public void setName(String arg0) {
	}

	public void setLayout(Layout arg0) {
	}

	public void setErrorHandler(ErrorHandler arg0) {
	}

	public boolean requiresLayout() {
		return false;
	}

	public String getName() {
		return null;
	}

	public Layout getLayout() {
		return null;
	}

	public Filter getFilter() {
		return null;
	}

	public ErrorHandler getErrorHandler() {
		return null;
	}

	public void doAppend(LoggingEvent arg0) {
		String message = arg0.getRenderedMessage();
		Logger logger = LoggerFactory.getLogger(arg0.getLocationInformation().getClassName());
		
		switch (arg0.getLevel().toInt()) {
		case Level.FATAL_INT:
			logger.error(message);
			break;
		case Level.ERROR_INT:
			logger.error(message);
			break;
		case Level.WARN_INT:
			logger.warn(message);
			break;
		case Level.INFO_INT:
			logger.info(message);
			break;
		case Level.DEBUG_INT:
			logger.debug(message);
			break;
		case Level.TRACE_INT:
			logger.trace(message);
			break;
		default:
			logger.error("Unknown LogLevel=" + arg0.getLevel().toInt());
			logger.error(arg0.getRenderedMessage());
			break;
		}

	}

	public void close() {
	}

	public void clearFilters() {
	}

	public void addFilter(Filter arg0) {
	}
}
