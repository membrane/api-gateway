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
package com.predic8.membrane.core.interceptor.formvalidation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.URLParamUtil;
public class FormValidationInterceptor extends AbstractInterceptor {

	public static class Field extends AbstractXmlElement {
		public String name;
		public String regex;
		
		private Pattern pattern;
		
		public boolean matchesSubstring(String input) {
			return pattern.matcher(input).matches();
		}

		@Override
		protected void parseAttributes(XMLStreamReader token) throws Exception {
			name = token.getAttributeValue("", "name");
			setRegex(token.getAttributeValue("", "regex"));
		}

		@Override
		public void write(XMLStreamWriter out) throws XMLStreamException {
			out.writeStartElement("field");

			out.writeAttribute("regex", regex);
			out.writeAttribute("name", name);

			out.writeEndElement();
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getRegex() {
			return regex;
		}

		public void setRegex(String regex) {
			this.regex = regex;
			pattern = Pattern.compile(regex);
		}

		
	}

	private static Log log = LogFactory.getLog(FormValidationInterceptor.class
			.getName());

	private List<Field> fields = new ArrayList<Field>();

	public FormValidationInterceptor() {
		name = "FormValidation";
		setFlow(Flow.REQUEST);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
	
		logMappings();
		
		Map<String, String> propMap = URLParamUtil.getParams(exc);
		for (Field f : fields) {
			if ( !propMap.containsKey(f.name) ) continue;
			
			if ( !f.matchesSubstring(propMap.get(f.name)) ) {
				setErrorResponse(exc, propMap, f);
				return Outcome.ABORT;
			}
		}
		return Outcome.CONTINUE;
	}

	private void setErrorResponse(Exchange exc, Map<String, String> propMap, Field f) {
		exc.setResponse(Response.badRequest(
				"Parameter "+f.name+"="+propMap.get(f.name)+" didn't match "+f.regex+"").build());
	}

	private void logMappings() {
		for (Field m : fields) {
			log.debug("[regex:"+m.regex+"],[name:"+m.name+"]");
		}
	}
	
	
	public List<Field> getFields() {
		return fields;
	}

	public void setFields(List<Field> fields) {
		this.fields = fields;
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("formValidation");

		for (Field m : fields) {
			m.write(out);
		}

		out.writeEndElement();
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws Exception {
		if (token.getLocalName().equals("field")) {
			fields.add((FormValidationInterceptor.Field)new FormValidationInterceptor.Field().parse(token));
		} else {
			super.parseChildren(token, child);
		}
	}
}
