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
