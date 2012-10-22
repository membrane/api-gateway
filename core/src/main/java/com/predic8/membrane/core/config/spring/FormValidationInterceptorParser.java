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

package com.predic8.membrane.core.config.spring;

import java.util.*;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.formvalidation.*;
import com.predic8.membrane.core.interceptor.formvalidation.FormValidationInterceptor.Field;

public class FormValidationInterceptorParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return FormValidationInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "formValidation");
		builder.addPropertyValue("fields",getFields(element));
	}

	private List<Field> getFields(Element e) {
		List<Field> fields = new ArrayList<Field>();
		for (Element f : DomUtils.getChildElementsByTagName(e, "field")) {
			Field field = new Field();
			field.setName(f.getAttribute("name"));
			field.setRegex(f.getAttribute("regex"));
			fields.add(field);
		}
		return fields;
	}
}
