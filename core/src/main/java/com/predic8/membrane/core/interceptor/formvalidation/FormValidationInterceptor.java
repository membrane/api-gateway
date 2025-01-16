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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import javax.xml.stream.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;

/**
 * @description Using the formValidation interceptor you can validate the input of HTML forms.
 * @topic 4. Interceptors/Features
 */
@MCElement(name="formValidation")
public class FormValidationInterceptor extends AbstractInterceptor {

	@MCElement(name="field", topLevel=false, id="formValidation-field")
	public static class Field extends AbstractXmlElement {
		public String name;
		public String regex;

		private Pattern pattern;

		public boolean matchesSubstring(String input) {
			return pattern.matcher(input).matches();
		}

		@Override
		protected void parseAttributes(XMLStreamReader token) {
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

		/**
		 * @description Name of the form parameter.
		 * @example age
		 */
		@Required
		@MCAttribute
		public void setName(String name) {
			this.name = name;
		}

		public String getRegex() {
			return regex;
		}

		/**
		 * @description Java Regular expression
		 * @example \d+
		 */
		@Required
		@MCAttribute
		public void setRegex(String regex) {
			this.regex = regex;
			pattern = Pattern.compile(regex);
		}


	}

	private static final Logger log = LoggerFactory.getLogger(FormValidationInterceptor.class
			.getName());

	private List<Field> fields = new ArrayList<>();

	public FormValidationInterceptor() {
		name = "FormValidation";
		setFlow(Flow.Set.REQUEST_FLOW);
	}

	@Override
	public Outcome handleRequest(Exchange exc) {

		logMappings();

        Map<String, String> propMap;
        try {
            propMap = URLParamUtil.getParams(router.getUriFactory(), exc, ERROR);
        } catch (Exception e) {
			ProblemDetails.internal(router.isProduction())
					.component(getDisplayName())
					.detail("Could not parse query parameters!")
					.extension("uri", exc.getRequest().getUri())
					.exception(e)
					.stacktrace(true)
					.buildAndSetResponse(exc);
			return ABORT;
        }
        for (Field f : fields) {
			if ( !propMap.containsKey(f.name) ) continue;

			if ( !f.matchesSubstring(propMap.get(f.name)) ) {
				setErrorResponse(exc, propMap, f);
				return ABORT;
			}
		}
		return CONTINUE;
	}

	private void setErrorResponse(Exchange exc, Map<String, String> propMap, Field f) {
		exc.setResponse(Response.badRequest(
				"Parameter "+f.name+"="+propMap.get(f.name)+" didn't match "+f.regex).build());
	}

	private void logMappings() {
		for (Field m : fields) {
			log.debug("[regex:{}],[name:{}]",m.regex,m.name);
		}
	}


	public List<Field> getFields() {
		return fields;
	}

	/**
	 * @description Specifies the name of the parameter and the regex to match against.
	 */
	@Required
	@MCChildElement
	public void setFields(List<Field> fields) {
		this.fields = fields;
	}

	@Override
	public String getDisplayName() {
		return "Form Validation";
	}


}
