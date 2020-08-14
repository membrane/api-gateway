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

import com.predic8.membrane.annot.MCChildElement;

/**
 * Mirrors {@link MCChildElement}.
 */
public class ChildElementInfo extends AbstractJavadocedInfo implements Comparable<ChildElementInfo> {
	private ElementInfo ei;
	private ExecutableElement e;
	private TypeElement typeDeclaration;
	private MCChildElement annotation;

	private String propertyName;
	private boolean list;
	private boolean required;

	@Override
	public int compareTo(ChildElementInfo o) {
		return getAnnotation().order() - o.getAnnotation().order();
	}

	public ElementInfo getEi() {
		return ei;
	}

	public void setEi(ElementInfo ei) {
		this.ei = ei;
	}

	public MCChildElement getAnnotation() {
		return annotation;
	}

	public void setAnnotation(MCChildElement annotation) {
		this.annotation = annotation;
	}

	public ExecutableElement getE() {
		return e;
	}

	public void setE(ExecutableElement e) {
		this.e = e;
		setDocedE(e);
	}

	public TypeElement getTypeDeclaration() {
		return typeDeclaration;
	}

	public void setTypeDeclaration(TypeElement typeDeclaration) {
		this.typeDeclaration = typeDeclaration;
	}

	public String getPropertyName() {
		return propertyName;
	}

	public void setPropertyName(String propertyName) {
		this.propertyName = propertyName;
	}

	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	public boolean isList() {
		return list;
	}

	public void setList(boolean list) {
		this.list = list;
	}
}