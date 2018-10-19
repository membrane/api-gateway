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

import java.util.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import com.predic8.membrane.annot.MCMain;

/**
 * Mirrors {@link MCMain}.
 */
public class MainInfo {
	private TypeElement element;
	private MCMain annotation;

	private List<ElementInfo> iis = new ArrayList<ElementInfo>();
	private Map<TypeElement, ChildElementDeclarationInfo> childElementDeclarations = new HashMap<TypeElement, ChildElementDeclarationInfo>();
	private Map<TypeElement, ElementInfo> elements = new TreeMap<TypeElement, ElementInfo>(new Comparator<TypeElement>() {
		@Override
		public int compare(TypeElement o1, TypeElement o2) {
			return o1.getSimpleName().toString().compareTo(o2.getSimpleName().toString());
		}
	});
	private Map<String, ElementInfo> globals = new HashMap<String, ElementInfo>();
	private Map<String, ElementInfo> ids = new HashMap<String, ElementInfo>();

	public List<Element> getInterceptorElements() {
		ArrayList<Element> res = new ArrayList<Element>(getIis().size());
		for (ElementInfo ii : getIis())
			res.add(ii.getElement());
		return res;
	}

	public TypeElement getElement() {
		return element;
	}

	public void setElement(TypeElement element) {
		this.element = element;
	}

	public MCMain getAnnotation() {
		return annotation;
	}

	public void setAnnotation(MCMain annotation) {
		this.annotation = annotation;
	}

	public List<ElementInfo> getIis() {
		return iis;
	}

	public Map<TypeElement, ElementInfo> getElements() {
		return elements;
	}

	public Map<String, ElementInfo> getGlobals() {
		return globals;
	}

	public Map<TypeElement, ChildElementDeclarationInfo> getChildElementDeclarations() {
		return childElementDeclarations;
	}

	public Map<String, ElementInfo> getIds() {
		return ids;
	}
}