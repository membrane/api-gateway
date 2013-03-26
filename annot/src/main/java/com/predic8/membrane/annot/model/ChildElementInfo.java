package com.predic8.membrane.annot.model;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import com.predic8.membrane.annot.MCChildElement;

public class ChildElementInfo implements Comparable<ChildElementInfo> {
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