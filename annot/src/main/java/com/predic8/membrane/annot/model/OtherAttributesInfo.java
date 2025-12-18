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
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.predic8.membrane.annot.AnnotUtils;
import com.predic8.membrane.annot.ProcessingException;

public class OtherAttributesInfo extends AbstractJavadocedInfo {

	private ExecutableElement otherAttributesSetter;
	private TypeElement mapValueType;

	public ExecutableElement getOtherAttributesSetter() {
		return otherAttributesSetter;
	}

	public void setOtherAttributesSetter(ExecutableElement otherAttributesSetter) {
		this.otherAttributesSetter = otherAttributesSetter;
		setDocedE(otherAttributesSetter);

        var typeArgs = ((DeclaredType) otherAttributesSetter.getParameters().getFirst().asType()).getTypeArguments();
		if (typeArgs.size() != 2) {
			throw new ProcessingException("@MCOtherAttributes must use Map<String, T>.", otherAttributesSetter);
		}

        mapValueType = (TypeElement) ((DeclaredType) typeArgs.get(1)).asElement();
	}

	public String getSpringName() {
		String s = getOtherAttributesSetter().getSimpleName().toString();
		if (!s.startsWith("set"))
			throw new ProcessingException("Setter method name is supposed to start with 'set'.", getOtherAttributesSetter());
		s = s.substring(3);
		return AnnotUtils.dejavaify(s);
	}

	public ValueType getValueType() {
		if (mapValueType.getQualifiedName().toString().equals("java.lang.String")) {
			return ValueType.STRING;
		}
		if (mapValueType.getQualifiedName().toString().equals("java.lang.Object")) {
			return ValueType.OBJECT;
		}
		throw new ProcessingException("Not supported: @McOtherAttributes void setAttr(Map<String, T> attrs) where T is neither String nor Object.");
	}

	public enum ValueType {
		STRING,
		OBJECT
	}
}
