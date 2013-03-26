package com.predic8.membrane.annot.model;

import java.util.ArrayList;
import java.util.List;

public class ChildElementDeclarationInfo {
	private List<ElementInfo> elementInfo = new ArrayList<ElementInfo>();
	private boolean raiseErrorWhenNoSpecimen;
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
}