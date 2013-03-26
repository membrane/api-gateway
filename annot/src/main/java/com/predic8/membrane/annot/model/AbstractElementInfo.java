package com.predic8.membrane.annot.model;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.TypeElement;


public class AbstractElementInfo {
	private TypeElement element;
	private boolean hasIdField;
	
	private TextContentInfo tci;
	
	private List<AttributeInfo> ais = new ArrayList<AttributeInfo>();
	private List<ChildElementInfo> ceis = new ArrayList<ChildElementInfo>();
	public TypeElement getElement() {
		return element;
	}
	public void setElement(TypeElement element) {
		this.element = element;
	}
	public TextContentInfo getTci() {
		return tci;
	}
	public void setTci(TextContentInfo tci) {
		this.tci = tci;
	}
	public List<AttributeInfo> getAis() {
		return ais;
	}
	public void setAis(List<AttributeInfo> ais) {
		this.ais = ais;
	}
	public boolean isHasIdField() {
		return hasIdField;
	}
	public void setHasIdField(boolean hasIdField) {
		this.hasIdField = hasIdField;
	}
	public List<ChildElementInfo> getCeis() {
		return ceis;
	}
	public void setCeis(List<ChildElementInfo> ceis) {
		this.ceis = ceis;
	}
}