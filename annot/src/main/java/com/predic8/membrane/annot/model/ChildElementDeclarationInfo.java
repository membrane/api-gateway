package com.predic8.membrane.annot.model;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.TypeElement;

/**
 * Meta-info mirror for an abstract thing like 'interface Interceptor'.
 * 
 * Used by some {@link MCChildElement}s, implemented by any number of {@link MCElement}s.
 */
public class ChildElementDeclarationInfo {
	private TypeElement target;
	private List<ElementInfo> elementInfo = new ArrayList<ElementInfo>();
	private boolean raiseErrorWhenNoSpecimen;
	private List<ChildElementInfo> usedBy = new ArrayList<ChildElementInfo>();
	
	public TypeElement getTarget() {
		return target;
	}
	
	public void setTarget(TypeElement target) {
		this.target = target;
	}
	
	public List<ElementInfo> getElementInfo() {
		return elementInfo;
	}
	public void setElementInfo(List<ElementInfo> elementInfo) {
		this.elementInfo = elementInfo;
	}
	public boolean isRaiseErrorWhenNoSpecimen() {
		return raiseErrorWhenNoSpecimen;
	}
	public void setRaiseErrorWhenNoSpecimen(boolean raiseErrorWhenNoSpecimen) {
		this.raiseErrorWhenNoSpecimen = raiseErrorWhenNoSpecimen;
	}
	
	public void addUsedBy(ChildElementInfo cei) {
		usedBy.add(cei);
	}
	
	public List<ChildElementInfo> getUsedBy() {
		return usedBy;
	}
}