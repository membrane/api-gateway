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

package com.predic8.membrane.core.interceptor.xmlprotection;

import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class XMLProtectionInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(XMLProtectionInterceptor.class.getName());

	private int maxAttibuteCount = 1000;
	private int maxElementNameLength = 1000;
	private boolean removeDTD = true;

	
	public XMLProtectionInterceptor() {
		name = "XML Protection";
		setFlow(Flow.REQUEST);
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (!exc.getRequest().isXML()) {
			log.debug("request discarded by xmlProtection because it is not XML");
			setFailResponse(exc);
			return Outcome.ABORT;
		}
		
		if (!protectXML(exc)) {
			log.warn("request discarded by xmlProtection, because it is not wellformed or exceeds limits");
			setFailResponse(exc);
			return Outcome.ABORT;
		}

		log.debug("protected against XML attacks");

		return Outcome.CONTINUE;
	}
	
	private void setFailResponse(Exchange exc) {
		exc.setResponse(Response.badRequest("Invalid XML features used in request.").build());
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		if (token.getAttributeValue("", "removeDTD") != null) {
			removeDTD = Boolean.parseBoolean(token.getAttributeValue("", "removeDTD"));
		} else {
			removeDTD = true;
		}
		if (token.getAttributeValue("", "maxElementNameLength") != null) {
			maxElementNameLength = Integer.parseInt(token.getAttributeValue("", "maxElementNameLength"));
		} else {
			maxElementNameLength = -1;
		}
		if (token.getAttributeValue("", "maxAttibuteCount") != null) {
			maxAttibuteCount = Integer.parseInt(token.getAttributeValue("", "maxAttibuteCount"));
		} else {
			maxAttibuteCount = -1;
		}
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {
		out.writeStartElement("xmlProtection");

		if (removeDTD == false) out.writeAttribute("removeDTD", "false");
		if (maxElementNameLength != -1) out.writeAttribute("maxElementNameLength", "" + maxElementNameLength);
		if (maxAttibuteCount != -1) out.writeAttribute("maxAttributeCount", "" + maxAttibuteCount);

		out.writeEndElement();
	}

	private boolean protectXML(Exchange exc) throws Exception {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		
		XMLProtector protector = new XMLProtector(new OutputStreamWriter(stream, getCharset(exc)), 
				removeDTD, maxElementNameLength, maxAttibuteCount);
		
		if (!protector.protect(new InputStreamReader(exc.getRequest().getBodyAsStream(), getCharset(exc) )))
			return false;
		exc.getRequest().setBodyContent(stream.toByteArray());
		return true;
	}

	/**
	 * If no charset is specified, use standard XML charset UTF-8.
	 */
	private String getCharset(Exchange exc) {
		String charset = exc.getRequest().getCharset();
		if (charset == null)
			return Constants.UTF_8;
		
		return charset;
	}

	public void setMaxAttibuteCount(int maxAttibuteCount) {
		this.maxAttibuteCount = maxAttibuteCount;
	}

	public void setMaxElementNameLength(int maxElementNameLength) {
		this.maxElementNameLength = maxElementNameLength;
	}

	public void setRemoveDTD(boolean removeDTD) {
		this.removeDTD = removeDTD;
	}
	
	@Override
	public String getShortDescription() {
		return "Protects agains XML attacks.";
	}
	
	@Override
	public String getHelpId() {
		return "xml-protection";
	}

}

