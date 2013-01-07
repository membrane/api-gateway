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

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.util.MessageUtil;

@MCElement(name="log", xsd="" +
		"					<xsd:sequence />\r\n" + 
		"					<xsd:attribute name=\"headerOnly\" default=\"true\" type=\"xsd:boolean\" />\r\n" + 
		"					<xsd:attribute name=\"category\" default=\"com.predic8.membrane.core.interceptor.LogInterceptor\" type=\"xsd:string\" />\r\n" + 
		"					<xsd:attribute name=\"level\" default=\"INFO\" type=\"xsd:string\" />\r\n" + 
		"")
public class LogInterceptor extends AbstractInterceptor {

	public enum Level {
		TRACE, DEBUG, INFO, WARN, ERROR, FATAL
	}

	private boolean headerOnly = false;
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

	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {

		if (token.getAttributeValue("", "headerOnly") != null) {
			headerOnly = Boolean.parseBoolean(token.getAttributeValue("",
					"headerOnly"));
		}

		if (token.getAttributeValue("", "category") != null) {
			category = token.getAttributeValue("", "category");
		}

		if (token.getAttributeValue("", "level") != null) {
			level = Level.valueOf(token.getAttributeValue("", "level"));
		}
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {
		out.writeStartElement("log");

		if (headerOnly)
			out.writeAttribute("headerOnly", "" + headerOnly);

		if (!LogInterceptor.class.getName().equals(category))
			out.writeAttribute("category", category);

		if (level != Level.WARN)
			out.writeAttribute("level", "" + level);

		out.writeEndElement();
	}

	public boolean isHeaderOnly() {
		return headerOnly;
	}

	public void setHeaderOnly(boolean headerOnly) {
		this.headerOnly = headerOnly;
	}

	public Level getLevel() {
		return level;
	}

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
		log(new String(MessageUtil.getContent(msg), msg.getCharset()));
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
