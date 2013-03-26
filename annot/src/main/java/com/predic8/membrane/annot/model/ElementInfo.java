package com.predic8.membrane.annot.model;

import com.predic8.membrane.annot.AnnotUtils;
import com.predic8.membrane.annot.MCElement;


public class ElementInfo extends AbstractElementInfo {
	private MCElement annotation;
	private boolean generateParserClass;

	public String getParserClassSimpleName() {
		if (getAnnotation().group().equals("interceptor"))
			return AnnotUtils.javaify(getAnnotation().name() + "InterceptorParser");
		else
			return AnnotUtils.javaify(getAnnotation().name() + "Parser");
	}
	
	public MainInfo getMain(Model m) {
		for (MainInfo main : m.getMains())
			if (main.getAnnotation().outputPackage().equals(getAnnotation().configPackage()))
				return main;
		return m.getMains().get(0);
	}

	public String getClassName(Model m) {
		return getMain(m).getAnnotation().outputPackage() + "." + getParserClassSimpleName();
	}

	public MCElement getAnnotation() {
		return annotation;
	}

	public void setAnnotation(MCElement annotation) {
		this.annotation = annotation;
	}

	public boolean isGenerateParserClass() {
		return generateParserClass;
	}

	public void setGenerateParserClass(boolean generateParserClass) {
		this.generateParserClass = generateParserClass;
	}
}