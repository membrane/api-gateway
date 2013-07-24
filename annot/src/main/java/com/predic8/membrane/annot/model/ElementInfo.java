package com.predic8.membrane.annot.model;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.TypeElement;

import com.predic8.membrane.annot.AnnotUtils;
import com.predic8.membrane.annot.MCElement;

/**
 * Mirrors {@link MCElement}.
 */
public class ElementInfo extends AbstractJavadocedInfo {
	private MCElement annotation;
	private boolean generateParserClass;
	private List<ChildElementDeclarationInfo> usedBy = new ArrayList<ChildElementDeclarationInfo>();
	
	
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
		setDocedE(element);
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

	public String getParserClassSimpleName() {
		if (getAnnotation().group().equals("interceptor"))
			return AnnotUtils.javaify(getAnnotation().name() + "InterceptorParser");
		else
			return AnnotUtils.javaify(getAnnotation().name() + "Parser");
	}
	
	public MainInfo getMain(Model m) {
		for (MainInfo main : m.getMains()) {
			main.getAnnotation();
			main.getAnnotation().outputPackage();
			main.getAnnotation().outputPackage().equals("");
			getAnnotation().configPackage();
			if (main.getAnnotation().outputPackage().equals(getAnnotation().configPackage()))
				return main;
		}
		return m.getMains().get(0);
	}

	public String getClassName(Model m) {
		return getMain(m).getAnnotation().outputPackage() + "." + getParserClassSimpleName();
	}
	
	public String getXSDTypeName(Model m) {
		return getClassName(m);
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
	
	public void addUsedBy(ChildElementDeclarationInfo cedi) {
		usedBy.add(cedi);
	}
	
	public List<ChildElementDeclarationInfo> getUsedBy() {
		return usedBy;
	}
	
	public String getId() {
		if (annotation.id().length() > 0)
			return annotation.id();
		return annotation.name();
	}
}