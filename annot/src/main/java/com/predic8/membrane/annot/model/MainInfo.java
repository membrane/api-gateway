package com.predic8.membrane.annot.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	private Map<String, List<ElementInfo>> groups = new HashMap<String, List<ElementInfo>>();
	private Map<TypeElement, ChildElementDeclarationInfo> childElementDeclarations = new HashMap<TypeElement, ChildElementDeclarationInfo>();
	private Map<TypeElement, ElementInfo> elements = new HashMap<TypeElement, ElementInfo>();
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

	public Map<String, List<ElementInfo>> getGroups() {
		return groups;
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