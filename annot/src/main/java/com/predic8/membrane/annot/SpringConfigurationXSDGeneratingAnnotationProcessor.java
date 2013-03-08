package com.predic8.membrane.annot;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes(value = { "com.predic8.membrane.annot.*" })
public class SpringConfigurationXSDGeneratingAnnotationProcessor extends AbstractProcessor {
	
	private class AttributeInfo {
		MCAttribute annotation;
		ExecutableElement e;
		boolean required;

		private String xsdType;
		private boolean isEnum;
		private boolean isBeanReference;
		
		public String getXMLName() {
			if (annotation.attributeName().length() == 0)
				return getSpringName();
			else
				return annotation.attributeName();
		}
		
		public String getSpringName() {
			String s = e.getSimpleName().toString();
			if (!s.substring(0, 3).equals("set"))
				throw new ProcessingException("Setter method name is supposed to start with 'set'.", e);
			s = s.substring(3);
			return dejavaify(s);
		}

		public String getXSDType() {
			analyze();
			return xsdType;
		}

		public boolean isEnum() {
			analyze();
			return isEnum;
		}
		
		public boolean isBeanReference() {
			analyze();
			return isBeanReference;
		}

		private void analyze() {
			if (xsdType != null) // already analyzed?
				return;
			
			if (e.getParameters().size() != 1)
				throw new ProcessingException("Setter is supposed to have 1 parameter.", e);
			VariableElement ve = e.getParameters().get(0);
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
				TypeElement e = (TypeElement) processingEnv.getTypeUtils().asElement(ve.asType());
				if (e.getQualifiedName().toString().equals("java.lang.String")) {
					xsdType = "xsd:string";
					return;
				}
				
				if (e.getSuperclass().getKind() == TypeKind.DECLARED) {
					TypeElement superClass = ((TypeElement)processingEnv.getTypeUtils().asElement(e.getSuperclass()));
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
				throw new ProcessingException("Not implemented: XSD type for " + ve.asType().getKind().toString(), this.e);
			}
		}
	}
	
	private static class AbstractElementInfo {
		TypeElement element;
		boolean hasIdField;
		
		TextContentInfo tci;
		
		List<AttributeInfo> ais = new ArrayList<AttributeInfo>();
		List<ChildElementInfo> ceis = new ArrayList<ChildElementInfo>();
	}
	
	private static class TextContentInfo {
		String propertyName;
	}
	
	private static class ChildElementInfo implements Comparable<ChildElementInfo> {
		ExecutableElement e;
		TypeElement typeDeclaration;
		MCChildElement annotation;
		
		String propertyName;
		boolean list;
		boolean required;
		
		@Override
		public int compareTo(ChildElementInfo o) {
			return annotation.order() - o.annotation.order();
		}
	}
	
	private static class ElementInfo extends AbstractElementInfo {
		MCElement annotation;
		boolean generateParserClass;

		public String getParserClassSimpleName() {
			if (annotation.group().equals("interceptor"))
				return javaify(annotation.name() + "InterceptorParser");
			else
				return javaify(annotation.name() + "Parser");
		}
		
		public MainInfo getMain(Model m) {
			for (MainInfo main : m.mains)
				if (main.annotation.outputPackage().equals(annotation.configPackage()))
					return main;
			return m.mains.get(0);
		}

		public String getClassName(Model m) {
			return getMain(m).annotation.outputPackage() + "." + getParserClassSimpleName();
		}
	}
	
	private static class ChildElementDeclarationInfo {
		List<ElementInfo> elementInfo = new ArrayList<ElementInfo>();
		boolean raiseErrorWhenNoSpecimen;
	}
	
	private static class MainInfo {
		TypeElement element;
		MCMain annotation;
		
		List<ElementInfo> iis = new ArrayList<ElementInfo>();
		Map<String, List<ElementInfo>> groups = new HashMap<String, List<ElementInfo>>();
		Map<TypeElement, ChildElementDeclarationInfo> childElementDeclarations = new HashMap<TypeElement, ChildElementDeclarationInfo>();
		Map<TypeElement, ElementInfo> elements = new HashMap<TypeElement, ElementInfo>();
		Map<String, ElementInfo> globals = new HashMap<String, ElementInfo>();
		
		List<Element> getInterceptorElements() {
			ArrayList<Element> res = new ArrayList<Element>(iis.size());
			for (ElementInfo ii : iis)
				res.add(ii.element);
			return res;
		}
	}
	
	private static class Model {
		List<MainInfo> mains = new ArrayList<MainInfo>();
	}
	
	private static class ProcessingException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		
		Element[] e;
		
		public ProcessingException(String message, Element... e) {
			super(message);
			this.e = e;
		}
	}
	
	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}
	
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		try {
			if (annotations.size() > 0) { // somehow we get called twice in the javac run from Maven

				Model m = new Model();
				
				Set<? extends Element> mcmains = roundEnv.getElementsAnnotatedWith(MCMain.class);
				if (mcmains.size() == 0) {
					processingEnv.getMessager().printMessage(Kind.WARNING, "@MCMain was nowhere found.");
					return true;
				}
				for (Element element : mcmains) {
					MainInfo main = new MainInfo();
					main.element = (TypeElement)element;
					main.annotation = element.getAnnotation(MCMain.class);
					m.mains.add(main);
				}

				for (Element e : roundEnv.getElementsAnnotatedWith(MCElement.class)) {
					ElementInfo ii = new ElementInfo();
					ii.element = (TypeElement)e;
					ii.annotation = e.getAnnotation(MCElement.class);
					ii.generateParserClass = processingEnv.getElementUtils().getTypeElement(ii.getClassName(m)) == null;
					MainInfo main = ii.getMain(m);
					main.iis.add(ii);
					
					if (!main.groups.containsKey(ii.annotation.group()))
						main.groups.put(ii.annotation.group(), new ArrayList<ElementInfo>());
					main.groups.get(ii.annotation.group()).add(ii);
					main.elements.put(ii.element, ii);
					if (main.globals.containsKey(ii.annotation.name()))
						throw new ProcessingException("Duplicate global @MCElement name.", main.globals.get(ii.annotation.name()).element, ii.element);
	
					scan(m, main, ii);
					
					if (ii.tci != null && !ii.annotation.mixed())
						throw new ProcessingException("@MCTextContent requires @MCElement(..., mixed=true) on the class.", ii.element);
					if (ii.tci == null && ii.annotation.mixed())
						throw new ProcessingException("@MCElement(..., mixed=true) requires @MCTextContent on a property.", ii.element);
				}
				
				for (MainInfo main : m.mains) {
					
					for (Map.Entry<TypeElement, ChildElementDeclarationInfo> f : main.childElementDeclarations.entrySet()) {
						ChildElementDeclarationInfo cedi = f.getValue();
						ElementInfo ei = main.elements.get(f.getKey());

						if (ei != null)
							cedi.elementInfo.add(ei);
						else {
							for (Map.Entry<TypeElement, ElementInfo> e : main.elements.entrySet())
								if (processingEnv.getTypeUtils().isAssignable(e.getKey().asType(), f.getKey().asType()))
									cedi.elementInfo.add(e.getValue());
						}

						if (cedi.elementInfo.size() == 0 && cedi.raiseErrorWhenNoSpecimen) {
							processingEnv.getMessager().printMessage(Kind.ERROR, "@MCChildElement references " + f.getKey().getQualifiedName() + ", but there is no @MCElement among it and its subclasses.", f.getKey());
							return true;
						}
					}
				}


				if (mcmains.size() == 0) {
					processingEnv.getMessager().printMessage(Kind.ERROR, "@MCMain but no @MCElement found.", mcmains.iterator().next());
					return true;
				}

				writeXSD(m);
				writeParsers(m);
				writeParserDefinitior(m);
			}

			return true;
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ProcessingException e1) {
			for (int i = 0; i < e1.e.length; i++)
				processingEnv.getMessager().printMessage(Kind.ERROR, i == 0 ? e1.getMessage() : "also here", e1.e[i]);
			return true;
		}
	}

	private static final String REQUIRED = "org.springframework.beans.factory.annotation.Required";
	
	private void scan(Model m, MainInfo main, AbstractElementInfo ii) {
		scan(m, main, ii, ii.element);
	}
	
	private void scan(Model m, MainInfo main, AbstractElementInfo ii, TypeElement te) {
		TypeMirror superclass = te.getSuperclass();
		if (superclass instanceof DeclaredType)
			scan(m, main, ii, (TypeElement) ((DeclaredType)superclass).asElement());
		
		for (Element e2 : te.getEnclosedElements()) {
			MCAttribute a = e2.getAnnotation(MCAttribute.class);
			if (a != null) {
				AttributeInfo ai = new AttributeInfo();
				ai.annotation = a;
				ai.e = (ExecutableElement) e2;
				ai.required = isRequired(e2);
				ii.ais.add(ai);
				ii.hasIdField = ii.hasIdField || ai.getXMLName().equals("id");
			}
			MCChildElement b = e2.getAnnotation(MCChildElement.class);
			if (b != null) {
				ChildElementInfo cei = new ChildElementInfo();
				cei.annotation = b;
				cei.e = (ExecutableElement) e2;
				TypeMirror setterArgType = cei.e.getParameters().get(0).asType();
				cei.typeDeclaration = (TypeElement) ((DeclaredType) setterArgType).asElement();
				cei.propertyName = dejavaify(e2.getSimpleName().toString().substring(3));
				cei.required = isRequired(e2);
				ii.ceis.add(cei);
				
				// unwrap "java.util.List<?>" and "java.util.Collection<?>"
				if (cei.typeDeclaration.getQualifiedName().toString().startsWith("java.util.List") ||
						cei.typeDeclaration.getQualifiedName().toString().startsWith("java.util.Collection")) {
					cei.typeDeclaration = (TypeElement) ((DeclaredType) ((DeclaredType) setterArgType).getTypeArguments().get(0)).asElement();
					cei.list = true;
				}
				
				if (!main.childElementDeclarations.containsKey(cei.typeDeclaration)) {
					ChildElementDeclarationInfo cedi = new ChildElementDeclarationInfo();
					cedi.raiseErrorWhenNoSpecimen = !cei.annotation.allowForeign();
					
					main.childElementDeclarations.put(cei.typeDeclaration, cedi);
				} else {
					ChildElementDeclarationInfo cedi = main.childElementDeclarations.get(cei.typeDeclaration);
					cedi.raiseErrorWhenNoSpecimen = cedi.raiseErrorWhenNoSpecimen || !cei.annotation.allowForeign();
				}
			}
			MCTextContent c = e2.getAnnotation(MCTextContent.class);
			if (c != null) {
				TextContentInfo tci = new TextContentInfo();
				tci.propertyName = dejavaify(e2.getSimpleName().toString().substring(3));
				ii.tci = tci;
			}
		}
		Collections.sort(ii.ceis);
	}

	private boolean isRequired(Element e2) {
		for (AnnotationMirror am : e2.getAnnotationMirrors())
			if (((TypeElement)am.getAnnotationType().asElement()).getQualifiedName().toString().equals(REQUIRED))
				return true;
		return false;
	}

	private void writeParserDefinitior(Model m) throws IOException {
		
		for (MainInfo main : m.mains) {
			List<Element> sources = new ArrayList<Element>();
			sources.addAll(main.getInterceptorElements());
			sources.add(main.element);

			FileObject o = processingEnv.getFiler().createSourceFile(
					main.annotation.outputPackage() + ".NamespaceHandlerAutoGenerated",
					sources.toArray(new Element[0]));
			BufferedWriter bw = new BufferedWriter(o.openWriter());
			try {
				bw.write("/* Copyright 2012,2013 predic8 GmbH, www.predic8.com\r\n" + 
						"\r\n" + 
						"   Licensed under the Apache License, Version 2.0 (the \"License\");\r\n" + 
						"   you may not use this file except in compliance with the License.\r\n" + 
						"   You may obtain a copy of the License at\r\n" + 
						"\r\n" + 
						"   http://www.apache.org/licenses/LICENSE-2.0\r\n" + 
						"\r\n" + 
						"   Unless required by applicable law or agreed to in writing, software\r\n" + 
						"   distributed under the License is distributed on an \"AS IS\" BASIS,\r\n" + 
						"   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\r\n" + 
						"   See the License for the specific language governing permissions and\r\n" + 
						"   limitations under the License. */\r\n" + 
						"\r\n" + 
						"package " + main.annotation.outputPackage() + ";\r\n" + 
						"\r\n" + 
						"public class NamespaceHandlerAutoGenerated {\r\n" + 
						"\r\n" + 
						"	public static void registerBeanDefinitionParsers(NamespaceHandler nh) {\r\n");
				for (ElementInfo i : main.iis) {
					bw.write("		nh.registerBeanDefinitionParser2(\"" + i.annotation.name() + "\", new " + i.getParserClassSimpleName() + "());\r\n");
				}
				bw.write(
						"	}\r\n" + 
						"}\r\n" + 
						"");
			} finally {
				bw.close();
			}
		}
	}

	private void writeXSD(Model m) throws IOException, ProcessingException {
		for (MainInfo main : m.mains) {
			List<Element> sources = new ArrayList<Element>();
			sources.add(main.element);
			sources.addAll(main.getInterceptorElements());

			FileObject o = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
					main.annotation.outputPackage(), main.annotation.outputName(), sources.toArray(new Element[0]));
			BufferedWriter bw = new BufferedWriter(o.openWriter());
			try {
				assembleXSD(m, main, bw);
			} finally {
				bw.close();
			}
		}
	}

	private void writeParsers(Model m) throws IOException {
		for (MainInfo main : m.mains) {
			for (ElementInfo ii : main.iis) {
				
				if (!ii.generateParserClass)
					continue;
				
				List<Element> sources = new ArrayList<Element>();
				sources.add(main.element);
				sources.add(ii.element);
				
				String interceptorClassName = ii.element.getQualifiedName().toString();
				
				FileObject o = processingEnv.getFiler().createSourceFile(main.annotation.outputPackage() + "." + ii.getParserClassSimpleName(),
						sources.toArray(new Element[0]));
				BufferedWriter bw = new BufferedWriter(o.openWriter());
				try {
					bw.write("/* Copyright 2012 predic8 GmbH, www.predic8.com\r\n" + 
							"\r\n" + 
							"   Licensed under the Apache License, Version 2.0 (the \"License\");\r\n" + 
							"   you may not use this file except in compliance with the License.\r\n" + 
							"   You may obtain a copy of the License at\r\n" + 
							"\r\n" + 
							"   http://www.apache.org/licenses/LICENSE-2.0\r\n" + 
							"\r\n" + 
							"   Unless required by applicable law or agreed to in writing, software\r\n" + 
							"   distributed under the License is distributed on an \"AS IS\" BASIS,\r\n" + 
							"   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\r\n" + 
							"   See the License for the specific language governing permissions and\r\n" + 
							"   limitations under the License. */\r\n" + 
							"\r\n" + 
							"package " + main.annotation.outputPackage() + ";\r\n" + 
							"\r\n" + 
							"import org.w3c.dom.Element;\r\n" + 
							"import org.springframework.beans.factory.xml.ParserContext;\r\n" + 
							"import org.springframework.beans.factory.support.BeanDefinitionBuilder;\r\n");
					if (!main.annotation.outputPackage().equals("com.predic8.membrane.core.config.spring"))
						bw.write("import com.predic8.membrane.core.config.spring.*;\r\n");
					bw.write(
							"\r\n" + 
							"public class " + ii.getParserClassSimpleName() + " extends AbstractParser {\r\n" + 
							"\r\n" + 
							"	protected Class<?> getBeanClass(org.w3c.dom.Element element) {\r\n" + 
							"		return " + interceptorClassName + ".class;\r\n" + 
							"	}\r\n");
					bw.write("	@Override\r\n" + 
							"	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {\r\n");
					if (ii.hasIdField)
						bw.write("		setPropertyIfSet(\"id\", element, builder);\r\n");
					bw.write(
							"		setIdIfNeeded(element, parserContext, \"" + ii.annotation.name() + "\");\r\n");
					for (AttributeInfo ai : ii.ais) {
						if (ai.getXMLName().equals("id"))
							continue;
						if (ai.isBeanReference()) {
							if (!ai.required)
								bw.write("		if (element.hasAttribute(\"" + ai.getXMLName() + "\"))\r\n");
							bw.write("		builder.addPropertyReference(\"" + ai.getSpringName() + "\", element.getAttribute(\"" + ai.getXMLName() + "\"));\r\n");
						} else {
							bw.write("		setProperty" + (ai.required ? "" : "IfSet") + "(\"" + ai.getXMLName() + "\", \"" + ai.getSpringName() + "\", element, builder" + (ai.isEnum() ? ", true" : "") + ");\r\n");
						}
						if (ai.getXMLName().equals("name"))
							bw.write("		element.removeAttribute(\"name\");\r\n");
					}
					for (ChildElementInfo cei : ii.ceis)
						if (cei.list)
							bw.write("		builder.addPropertyValue(\"" + cei.propertyName + "\", new java.util.ArrayList<Object>());\r\n");
					if (ii.tci != null)
						bw.write("		builder.addPropertyValue(\"" + ii.tci.propertyName + "\", element.getTextContent());\r\n");
					else
						bw.write("		parseChildren(element, parserContext, builder);\r\n");
					for (ChildElementInfo cei : ii.ceis)
						if (cei.list && cei.required) {
							bw.write("		if (builder.getBeanDefinition().getPropertyValues().getPropertyValue(\"" + cei.propertyName + "[0]\") == null)\r\n");
							bw.write("			throw new RuntimeException(\"Property '" + cei.propertyName + "' is required, but none was defined (empty list).\");\r\n");
						}
					
					bw.write(
							"	}\r\n" + 
							"");
	
					bw.write(
							"@Override\r\n" +
							"protected void handleChildObject(Element ele, ParserContext parserContext, BeanDefinitionBuilder builder, Class<?> clazz, Object child) {\r\n");
					for (ChildElementInfo cei : ii.ceis) {
						bw.write(
								"	if (" + cei.typeDeclaration.getQualifiedName() + ".class.isAssignableFrom(clazz)) {\r\n" + 
								"		builder.addPropertyValue(\"" + cei.propertyName + "\"" + (cei.list ? "+\"[\"+ incrementCounter(builder, \"" + cei.propertyName + "\") + \"]\" " : "") + ", child);\r\n" + 
								"	} else \r\n");
					}
					bw.write(
								"	{\r\n" +
								"		throw new RuntimeException(\"Unknown child class \\\"\" + clazz + \"\\\".\");\r\n" +
								"	}\r\n");
					bw.write(
							"}\r\n");
					
					bw.write(
							"}\r\n" + 
							"");
				} finally {
					bw.close();
				}
			}
		}

	}

	private static String javaify(String s) {
		StringBuilder sb = new StringBuilder(s);
		sb.replace(0, 1, "" + Character.toUpperCase(s.charAt(0)));
		return sb.toString();
	}

	private String dejavaify(String s) {
		StringBuilder sb = new StringBuilder(s);
		sb.replace(0, 1, "" + Character.toLowerCase(s.charAt(0)));
		return sb.toString();
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
		String xsd = getXSDTemplate(main.annotation.targetNamespace()); 
		xsd = xsd.replace("${declarations}", assembleDeclarations(m, main));
		for (String group : main.groups.keySet()) {
			xsd = xsd.replace("${" + group + "Declarations}", "");
			xsd = xsd.replace("${" + group + "References}", assembleInterceptorReferences(m, main, group));
		}
		bw.append(xsd);
	}

	private String assembleDeclarations(Model m, MainInfo main) throws ProcessingException {
		StringWriter declarations = new StringWriter();
		for (ElementInfo i : main.elements.values()) {
			declarations.append(assembleElementDeclaration(m, main, i));
		}
		return declarations.toString();
	}

	private CharSequence assembleElementDeclaration(Model m, MainInfo main, ElementInfo i) throws ProcessingException {
		String xsd;
		if (i.annotation.xsd().length() == 0) {
			if (i.annotation.mixed() && i.ceis.size() > 0) {
				throw new ProcessingException(
						"@MCElement(..., mixed=true) and @MCTextContent is not compatible with @MCChildElement.",
						i.element);
			}
			if (i.ais.size() > 0 || i.ceis.size() > 0 || i.annotation.mixed()) {
				xsd = assembleElementInfo(m, main, i);
			} else {
				return "<xsd:element name=\""+ i.annotation.name() + "\" type=\"EmptyElementType\" />\r\n";
			}
		} else {
			xsd = i.annotation.xsd();
		}
		return
				"<xsd:element name=\""+ i.annotation.name() +"\">\r\n" + 
				"	<xsd:complexType>\r\n" + 
				"		<xsd:complexContent " + (i.annotation.mixed() ? "mixed=\"true\"" : "") + ">\r\n" + 
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
		for (ChildElementInfo cei : i.ceis) {
			xsd.append("<xsd:choice" + (cei.required ? " minOccurs=\"1\"" : " minOccurs=\"0\"") + (cei.list ? " maxOccurs=\"unbounded\"" : "") + ">\r\n");
			for (ElementInfo ei : main.childElementDeclarations.get(cei.typeDeclaration).elementInfo)
				xsd.append("<xsd:element ref=\"" + ei.annotation.name() + "\" />\r\n");
			if (cei.annotation.allowForeign())
				xsd.append("<xsd:any namespace=\"##other\" processContents=\"strict\" />\r\n");
			xsd.append("</xsd:choice>\r\n");
		}
		xsd.append("</xsd:sequence>\r\n");
		for (AttributeInfo ai : i.ais)
			if (!ai.getXMLName().equals("id"))
				xsd.append(assembleAttributeDeclaration(ai));
		return xsd.toString();
	}

	private String assembleAttributeDeclaration(AttributeInfo ai) {
		// TODO: default value
		return "<xsd:attribute name=\"" + ai.getXMLName() + "\" type=\"" + ai.getXSDType() + "\" "
				+ (ai.required ? "use=\"required\"" : "") + " />\r\n";
	}

	private String assembleInterceptorReferences(Model m, MainInfo main, String group) {
		StringWriter interceptorReferences = new StringWriter();
		for (ElementInfo i : main.groups.get(group)) {
			interceptorReferences.append("<xsd:element ref=\"" + i.annotation.name() + "\" />\r\n");
		}
		return interceptorReferences.toString();
	}
}