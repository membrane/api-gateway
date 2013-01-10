/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

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

import java.io.IOException;

import javax.xml.stream.*;

import org.apache.commons.logging.*;
import org.springframework.beans.factory.annotation.Required;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.util.MessageUtil;

/**
 * Runs a regular-expression-replacement on either the message body (default) or
 * all header values.
 */
@MCElement(name="regExReplacer")
public class RegExReplaceInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(RegExReplaceInterceptor.class.getName());

	private String regex;
	
	private String replace;
	
	private TargetType target = TargetType.BODY;
	
	public enum TargetType {
		BODY,
		HEADER
	}
	
	public RegExReplaceInterceptor() {
		name="Regex Replacer";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (target == TargetType.HEADER)
			replaceHeader(exc.getRequest().getHeader());
		else
			replaceBody(exc.getRequest());

		return Outcome.CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		if (target == TargetType.HEADER)
			replaceHeader(exc.getResponse().getHeader());
		else
			replaceBody(exc.getResponse());
		return Outcome.CONTINUE;
	}
	
	private void replaceHeader(Header header) {
		for (HeaderField hf : header.getAllHeaderFields())
			hf.setValue(hf.getValue().replaceAll(regex, replace));
	}

	private void replaceBody(Message res) throws IOException, Exception {
		if (hasNoTextContent(res) ) return; 
		
		log.debug("pattern: " +regex);
		log.debug("replacement: " +replace);
		
		res.readBody();
		byte[] content = MessageUtil.getContent(res);
		res.setBodyContent(new String(content, res.getCharset()).replaceAll(regex, replace).getBytes(res.getCharset()));
		res.getHeader().removeFields("Content-Encoding");
	}

	private boolean hasNoTextContent(Message res) throws Exception {		
		return res.isBodyEmpty() || !res.isXML() && !res.isHTML();
	}

	public String getRegex() {
		return regex;
	}

	@Required
	@MCAttribute
	public void setRegex(String regex) {
		this.regex = regex;
	}

	public String getReplace() {
		return replace;
	}

	@Required
	@MCAttribute
	public void setReplace(String replace) {
		this.replace = replace;
	}
	
	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("regExReplacer");

		out.writeAttribute("regex", regex);
		out.writeAttribute("replace", replace);
		if (target == TargetType.HEADER)
			out.writeAttribute("target", "HEADER");

		out.writeEndElement();
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) {
		regex = token.getAttributeValue("", "regex");
		replace = token.getAttributeValue("", "replace");
		String targetStr = token.getAttributeValue("", "target");
		target = targetStr == null ? TargetType.BODY : TargetType.valueOf(targetStr);
	}
	
	@Override
	public String getHelpId() {
		return "regex-replacer";
	}
	
	public TargetType getTarget() {
		return target;
	}
	
	@MCAttribute
	public void setTarget(TargetType target) {
		this.target = target;
	}
	
}
