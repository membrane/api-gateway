package com.predic8.membrane.annot.model;

import javax.lang.model.element.ExecutableElement;

import com.predic8.membrane.annot.AnnotUtils;
import com.predic8.membrane.annot.ProcessingException;

public class OtherAttributesInfo extends AbstractJavadocedInfo {
	
	private ExecutableElement otherAttributesSetter;

	public ExecutableElement getOtherAttributesSetter() {
		return otherAttributesSetter;
	}
	
	public void setOtherAttributesSetter(ExecutableElement otherAttributesSetter) {
		this.otherAttributesSetter = otherAttributesSetter;
		setDocedE(otherAttributesSetter);
	}

	public String getSpringName() {
		String s = getOtherAttributesSetter().getSimpleName().toString();
		if (!s.substring(0, 3).equals("set"))
			throw new ProcessingException("Setter method name is supposed to start with 'set'.", getOtherAttributesSetter());
		s = s.substring(3);
		return AnnotUtils.dejavaify(s);
	}

}
