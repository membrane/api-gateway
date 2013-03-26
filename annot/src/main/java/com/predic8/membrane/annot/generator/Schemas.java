package com.predic8.membrane.annot.generator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.predic8.membrane.annot.ProcessingException;
import com.predic8.membrane.annot.model.AbstractElementInfo;
import com.predic8.membrane.annot.model.AttributeInfo;
import com.predic8.membrane.annot.model.ChildElementInfo;
import com.predic8.membrane.annot.model.ElementInfo;
import com.predic8.membrane.annot.model.MainInfo;
import com.predic8.membrane.annot.model.Model;

public class Schemas {
	
	private ProcessingEnvironment processingEnv;

	public Schemas(ProcessingEnvironment processingEnv) {
		this.processingEnv = processingEnv;
	}

	public void writeXSD(Model m) throws IOException, ProcessingException {
		for (MainInfo main : m.getMains()) {
			List<Element> sources = new ArrayList<Element>();
			sources.add(main.getElement());
			sources.addAll(main.getInterceptorElements());

			FileObject o = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
					main.getAnnotation().outputPackage(), main.getAnnotation().outputName(), sources.toArray(new Element[0]));
			BufferedWriter bw = new BufferedWriter(o.openWriter());
			try {
				assembleXSD(m, main, bw);
			} finally {
				bw.close();
			}
		}
	}

	private String getXSDTemplate(String namespace) {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" + 
				"<xsd:schema xmlns=\"" + namespace + "\"\r\n" + 
				"	xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:beans=\"http://www.springframework.org/schema/beans\"\r\n" + 
				"	targetNamespace=\"" + namespace + "\"\r\n" + 
				"	elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">\r\n" + 
				"\r\n" + 
				"	<xsd:import namespace=\"http://www.springframework.org/schema/beans\" schemaLocation=\"http://www.springframework.org/schema/beans/spring-beans-3.1.xsd\" />\r\n" + 
				"\r\n" + 
				"${declarations}\r\n" +
				"	\r\n" + 
				"	<xsd:complexType name=\"EmptyElementType\">\r\n" + 
				"		<xsd:complexContent>\r\n" + 
				"			<xsd:extension base=\"beans:identifiedType\">\r\n" + 
				"				<xsd:sequence />\r\n" + 
				"			</xsd:extension>\r\n" + 
				"		</xsd:complexContent>\r\n" + 
				"	</xsd:complexType>\r\n" + 
				"	\r\n" + 
				"</xsd:schema>";
	}
	
	private void assembleXSD(Model m, MainInfo main, BufferedWriter bw) throws IOException, ProcessingException {
		String xsd = getXSDTemplate(main.getAnnotation().targetNamespace()); 
		xsd = xsd.replace("${declarations}", assembleDeclarations(m, main));
		for (String group : main.getGroups().keySet()) {
			xsd = xsd.replace("${" + group + "Declarations}", "");
			xsd = xsd.replace("${" + group + "References}", assembleInterceptorReferences(m, main, group));
		}
		bw.append(xsd);
	}

	private String assembleDeclarations(Model m, MainInfo main) throws ProcessingException {
		StringWriter declarations = new StringWriter();
		for (ElementInfo i : main.getElements().values()) {
			declarations.append(assembleElementDeclaration(m, main, i));
		}
		return declarations.toString();
	}

	private CharSequence assembleElementDeclaration(Model m, MainInfo main, ElementInfo i) throws ProcessingException {
		String xsd;
		if (i.getAnnotation().xsd().length() == 0) {
			if (i.getAnnotation().mixed() && i.getCeis().size() > 0) {
				throw new ProcessingException(
						"@MCElement(..., mixed=true) and @MCTextContent is not compatible with @MCChildElement.",
						i.getElement());
			}
			if (i.getAis().size() > 0 || i.getCeis().size() > 0 || i.getAnnotation().mixed()) {
				xsd = assembleElementInfo(m, main, i);
			} else {
				return "<xsd:element name=\""+ i.getAnnotation().name() + "\" type=\"EmptyElementType\" />\r\n";
			}
		} else {
			xsd = i.getAnnotation().xsd();
		}
		return
				"<xsd:element name=\""+ i.getAnnotation().name() +"\">\r\n" + 
				"	<xsd:complexType>\r\n" + 
				"		<xsd:complexContent " + (i.getAnnotation().mixed() ? "mixed=\"true\"" : "") + ">\r\n" + 
				"			<xsd:extension base=\"beans:identifiedType\">\r\n" + 
				xsd +
				"			</xsd:extension>\r\n" + 
				"		</xsd:complexContent>\r\n" + 
				"	</xsd:complexType>\r\n" + 
				"</xsd:element>\r\n";
	}

	private String assembleElementInfo(Model m, MainInfo main, AbstractElementInfo i) {
		StringBuilder xsd = new StringBuilder();
		xsd.append("<xsd:sequence>\r\n");
		for (ChildElementInfo cei : i.getCeis()) {
			xsd.append("<xsd:choice" + (cei.isRequired() ? " minOccurs=\"1\"" : " minOccurs=\"0\"") + (cei.isList() ? " maxOccurs=\"unbounded\"" : "") + ">\r\n");
			for (ElementInfo ei : main.getChildElementDeclarations().get(cei.getTypeDeclaration()).getElementInfo())
				xsd.append("<xsd:element ref=\"" + ei.getAnnotation().name() + "\" />\r\n");
			if (cei.getAnnotation().allowForeign())
				xsd.append("<xsd:any namespace=\"##other\" processContents=\"strict\" />\r\n");
			xsd.append("</xsd:choice>\r\n");
		}
		xsd.append("</xsd:sequence>\r\n");
		for (AttributeInfo ai : i.getAis())
			if (!ai.getXMLName().equals("id"))
				xsd.append(assembleAttributeDeclaration(ai));
		return xsd.toString();
	}

	private String assembleAttributeDeclaration(AttributeInfo ai) {
		// TODO: default value
		return "<xsd:attribute name=\"" + ai.getXMLName() + "\" type=\"" + ai.getXSDType(processingEnv.getTypeUtils()) + "\" "
				+ (ai.isRequired() ? "use=\"required\"" : "") + " />\r\n";
	}

	private String assembleInterceptorReferences(Model m, MainInfo main, String group) {
		StringWriter interceptorReferences = new StringWriter();
		for (ElementInfo i : main.getGroups().get(group)) {
			interceptorReferences.append("<xsd:element ref=\"" + i.getAnnotation().name() + "\" />\r\n");
		}
		return interceptorReferences.toString();
	}

}
