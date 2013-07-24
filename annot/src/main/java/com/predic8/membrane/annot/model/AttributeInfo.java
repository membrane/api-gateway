package com.predic8.membrane.annot.model;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Types;

import com.predic8.membrane.annot.AnnotUtils;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.ProcessingException;

/**
 * Mirrors {@link MCAttribute}.
 */
public class AttributeInfo extends AbstractJavadocedInfo {
	private MCAttribute annotation;
	private ExecutableElement e;
	private boolean required;

	private String xsdType;
	private boolean isEnum;
	private boolean isBeanReference;
	
	public String getXMLName() {
		if (getAnnotation().attributeName().length() == 0)
			return getSpringName();
		else
			return getAnnotation().attributeName();
	}
	
	public String getSpringName() {
		String s = getE().getSimpleName().toString();
		if (!s.substring(0, 3).equals("set"))
			throw new ProcessingException("Setter method name is supposed to start with 'set'.", getE());
		s = s.substring(3);
		return AnnotUtils.dejavaify(s);
	}

	public String getXSDType(Types typeUtils) {
		analyze(typeUtils);
		return xsdType;
	}

	public boolean isEnum(Types typeUtils) {
		analyze(typeUtils);
		return isEnum;
	}
	
	public boolean isBeanReference(Types typeUtils) {
		analyze(typeUtils);
		return isBeanReference;
	}

	private void analyze(Types typeUtils) {
		if (xsdType != null) // already analyzed?
			return;
		
		if (getE().getParameters().size() != 1)
			throw new ProcessingException("Setter is supposed to have 1 parameter.", getE());
		VariableElement ve = getE().getParameters().get(0);
		switch (ve.asType().getKind()) {
		case INT:
			xsdType = "xsd:int";
			return;
		case LONG:
			xsdType = "xsd:long";
			return;
		case BOOLEAN:
			xsdType = "xsd:boolean";
			return;
		case DECLARED:
			TypeElement e = (TypeElement) typeUtils.asElement(ve.asType());
			if (e.getQualifiedName().toString().equals("java.lang.String")) {
				xsdType = "xsd:string";
				return;
			}
			
			if (e.getSuperclass().getKind() == TypeKind.DECLARED) {
				TypeElement superClass = ((TypeElement)typeUtils.asElement(e.getSuperclass()));
				if (superClass.getQualifiedName().toString().equals("java.lang.Enum")) {
					isEnum = true;
					xsdType = "xsd:string"; // TODO: restriction
				/*
				 *	<xsd:attribute name=\"target\" use=\"optional\" default=\"body\">\r\n" + 
				 *		<xsd:simpleType>\r\n" + 
				 *			<xsd:restriction base=\"xsd:string\">\r\n" + 
				 *				<xsd:enumeration value=\"body\" />\r\n" + 
				 *				<xsd:enumeration value=\"header\" />\r\n" + 
				 *			</xsd:restriction>\r\n" + 
				 *		</xsd:simpleType>\r\n" + 
				 *	</xsd:attribute>\r\n"
				 */
					return;
				}
			}
			
			isBeanReference = true;
			xsdType = "xsd:string";
			return;
		default:
			throw new ProcessingException("Not implemented: XSD type for " + ve.asType().getKind().toString(), this.getE());
		}
	}

	public MCAttribute getAnnotation() {
		return annotation;
	}

	public void setAnnotation(MCAttribute annotation) {
		this.annotation = annotation;
	}

	public ExecutableElement getE() {
		return e;
	}

	public void setE(ExecutableElement e) {
		this.e = e;
		setDocedE(e);
	}

	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}
}