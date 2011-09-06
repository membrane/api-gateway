/* Copyright 2009 predic8 GmbH, www.predic8.com

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

import javax.xml.stream.*;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.util.MessageUtil;

public class LogInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(LogInterceptor.class.getName());
	private boolean headerOnly = false;
	
	public LogInterceptor() {
		name = "Log";
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		log.info("==== Request ===");
		logMessage(exc.getRequest());
		return Outcome.CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		log.info("==== Response ===");
		logMessage(exc.getResponse());
		return Outcome.CONTINUE;
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		if ( token.getAttributeValue("", "headerOnly")!=null ) {
			headerOnly = Boolean.parseBoolean(token.getAttributeValue("", "headerOnly"));
		}
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {
		out.writeStartElement("log");

		if (headerOnly) out.writeAttribute("headerOnly", ""+headerOnly);

		out.writeEndElement();
	}

	public boolean isHeaderOnly() {
		return headerOnly;
	}

	public void setHeaderOnly(boolean headerOnly) {
		this.headerOnly = headerOnly;
	}

	private void logMessage(Message msg) throws Exception {
		log.info(msg==null?"N/A":msg);
		if (msg == null) { 
			log.info("no message");
			log.info("================");
			return;
		}
				
		log.info(msg.getStartLine());
		log.info("Headers:");
		log.info(msg.getHeader().toString());
		if (headerOnly) {
			log.info("================");
			return;			
		}
		
		log.info("Body:");
		if (msg.isBodyEmpty()) {
			log.info("empty");
			log.info("================");
			return;						
		}
		
		if (msg.isImage()) {
			log.info("[binary image data]");
			log.info("================");
			return;			
		}
		log.info(new String(MessageUtil.getContent(msg)));
		log.info("================");
	}
}

