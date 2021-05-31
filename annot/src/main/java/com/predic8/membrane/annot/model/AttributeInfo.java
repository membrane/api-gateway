/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.annot.model;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Types;

import com.predic8.membrane.annot.AnnotUtils;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.ProcessingException;

import java.util.HashMap;
import java.util.Map;

/**
 * Mirrors {@link MCAttribute}.
 */
public class AttributeInfo extends AbstractJavadocedInfo {
	private MCAttribute annotation;
	private ExecutableElement e;
	private boolean required;

	private String xsdType;
	private boolean isEnum;
	private boolean isBeanReference;

	public String getXMLName() {
		if (getAnnotation().attributeName().length() == 0)
			return getSpringName();
		else
			return getAnnotation().attributeName();
	}

	public String getSpringName() {
		String s = getE().getSimpleName().toString();
		if (!s.substring(0, 3).equals("set"))
			throw new ProcessingException("Setter method name is supposed to start with 'set'.", getE());
		s = s.substring(3);
		return AnnotUtils.dejavaify(s);
	}

	public String getSchemaType(Types typeUtils) {
		String xsdType = getXSDType(typeUtils);

		Map<String, String> mapping = new HashMap<>();
		mapping.put("spel_number", "integer");
		mapping.put("xsd:double", "number");
		mapping.put("spel_boolean", "boolean");
		mapping.put("xsd:string", "string");

		return mapping.get(xsdType);
	}

	public String getXSDType(Types typeUtils) {
		analyze(typeUtils);
		return xsdType;
	}

	public boolean isEnum(Types typeUtils) {
		analyze(typeUtils);
		return isEnum;
	}

	public boolean isBeanReference(Types typeUtils) {
		analyze(typeUtils);
		return isBeanReference;
	}

	private void analyze(Types typeUtils) {
		if (xsdType != null) // already analyzed?
			return;

		if (getE().getParameters().size() != 1)
			throw new ProcessingException("Setter is supposed to have 1 parameter.", getE());
		VariableElement ve = getE().getParameters().get(0);
		switch (ve.asType().getKind()) {
		case INT:
			xsdType = "spel_number";
			return;
		case LONG:
			xsdType = "spel_number";
			return;
		case DOUBLE:
			xsdType = "xsd:double";
			return;
		case BOOLEAN:
			xsdType = "spel_boolean";
			return;
		case DECLARED:
			TypeElement e = (TypeElement) typeUtils.asElement(ve.asType());
			if (e.getQualifiedName().toString().equals("java.lang.String")) {
				xsdType = "xsd:string";
				return;
			}

			if (e.getSuperclass().getKind() == TypeKind.DECLARED) {
				TypeElement superClass = ((TypeElement)typeUtils.asElement(e.getSuperclass()));
				if (superClass.getQualifiedName().toString().equals("java.lang.Enum")) {
					isEnum = true;
					xsdType = "xsd:string"; // TODO: restriction, but be carefull about Spring EL usage, for example "#{config.XXX}"
					/*
					 *	<xsd:attribute name=\"target\" use=\"optional\" default=\"body\">\r\n" +
					 *		<xsd:simpleType>\r\n" +
					 *			<xsd:restriction base=\"xsd:string\">\r\n" +
					 *				<xsd:enumeration value=\"body\" />\r\n" +
					 *				<xsd:enumeration value=\"header\" />\r\n" +
					 *			</xsd:restriction>\r\n" +
					 *		</xsd:simpleType>\r\n" +
					 *	</xsd:attribute>\r\n"
					 */
					return;
				}
			}

			isBeanReference = true;
			xsdType = "xsd:string";
			return;
		default:
			throw new ProcessingException("Not implemented: XSD type for " + ve.asType().getKind().toString(), this.getE());
		}
	}

	public MCAttribute getAnnotation() {
		return annotation;
	}

	public void setAnnotation(MCAttribute annotation) {
		this.annotation = annotation;
	}

	public ExecutableElement getE() {
		return e;
	}

	public void setE(ExecutableElement e) {
		this.e = e;
		setDocedE(e);
	}

	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}
}