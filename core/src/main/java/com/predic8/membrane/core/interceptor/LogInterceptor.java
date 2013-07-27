/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor;

import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;

/**
 * @description The log feature logs request and response messages to the log4j
 *              framework. The messages will appear either on the console or in
 *              a log file depending on the configuration of the
 *              <i>conf/log4j.properties</i> file.
 * @topic 5. Monitoring, Logging and Statistics
 */
@MCElement(name="log")
public class LogInterceptor extends AbstractInterceptor {

	public enum Level {
		TRACE, DEBUG, INFO, WARN, ERROR, FATAL
	}

	private boolean headerOnly = true;
	private String category = LogInterceptor.class.getName();
	private Level level = Level.INFO;

	public LogInterceptor() {
		name = "Log";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		log("==== Request ===");
		logMessage(exc.getRequest());
		return Outcome.CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		log("==== Response ===");
		logMessage(exc.getResponse());
		return Outcome.CONTINUE;
	}

	public boolean isHeaderOnly() {
		return headerOnly;
	}

	/**
	 * @default false
	 * @description If set to true only the HTTP header will be logged.
	 */
	@MCAttribute
	public void setHeaderOnly(boolean headerOnly) {
		this.headerOnly = headerOnly;
	}

	public Level getLevel() {
		return level;
	}

	/**
	 * @default INFO
	 * @description Sets the log level.
	 * @example WARN
	 */
	@MCAttribute
	public void setLevel(Level level) {
		this.level = level;
	}

	private void logMessage(Message msg) throws Exception {
		if (msg == null) {
			log("no message");
			log("================");
			return;
		}

		log(msg.getStartLine());
		log("Headers:");
		log(msg.getHeader().toString());
		if (headerOnly) {
			log("================");
			return;
		}

		log("Body:");
		if (msg.isBodyEmpty()) {
			log("empty");
			log("================");
			return;
		}

		if (msg.isImage()) {
			log("[binary image data]");
			log("================");
			return;
		}
		log(msg.getBodyAsStringDecoded());
		log("================");
	}

	private void log(String msg) {
		switch (level) {
		case TRACE:
			LogFactory.getLog(category).trace(msg);
			break;
		case DEBUG:
			LogFactory.getLog(category).debug(msg);
			break;
		case INFO:
			LogFactory.getLog(category).info(msg);
			break;
		case WARN:
			LogFactory.getLog(category).warn(msg);
			break;
		case ERROR:
			LogFactory.getLog(category).error(msg);
			break;
		case FATAL:
			LogFactory.getLog(category).fatal(msg);
			break;
		}

	}

	public String getCategory() {
		return category;
	}

	/**
	 * @default com.predic8.membrane.core.interceptor.LogInterceptor
	 * @description Sets the category of the logged message.
	 * @example Membrane
	 */
	@MCAttribute
	public void setCategory(String category) {
		this.category = category;
	}
	
	@Override
	public String getShortDescription() {
		return "Logs the " + (headerOnly ? "headers of " : "") + "requests and responses" + 
				" using Log4J's " + level.toString() + " level.";
	}
	
	@Override
	public String getHelpId() {
		return "log";
	}

}
