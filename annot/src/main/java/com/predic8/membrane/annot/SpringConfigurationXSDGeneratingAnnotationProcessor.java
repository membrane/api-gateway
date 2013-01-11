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
import javax.annotation.processing.SupportedSourceVersion;
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
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class SpringConfigurationXSDGeneratingAnnotationProcessor extends AbstractProcessor {
	
	private class AttributeInfo {
		ExecutableElement e;
		boolean required;

		private String xsdType;
		private boolean isEnum;
		
		public String getName() {
			String s = e.getSimpleName().toString();
			if (!s.substring(0, 3).equals("set"))
				throw new ProcessingException("Setter method name is supposed to start with 'set'.", e);
			s = s.substring(3);
			s = Character.toLowerCase(s.charAt(0)) + s.substring(1);
			return s;
		}

		public String getXSDType() {
			analyze();
			return xsdType;
		}

		public boolean isEnum() {
			analyze();
			return isEnum;
		}

		private void analyze() {
			if (xsdType != null)
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
				throw new ProcessingException("Not implemented: XSD type for " + e.getQualifiedName(), this.e);
			default:
				throw new ProcessingException("Not implemented: XSD type for " + ve.asType().getKind().toString(), this.e);
			}
		}
	}
	
	private static class AbstractElementInfo {
		TypeElement element;
		boolean hasIdField;
		
		List<AttributeInfo> ais = new ArrayList<AttributeInfo>();
		List<ChildElementInfo> ceis = new ArrayList<ChildElementInfo>();
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
	}
	
	private static class ChildElementDeclarationInfo {
		List<ElementInfo> elementInfo = new ArrayList<ElementInfo>();
	}
	
	private static class Model {
		TypeElement mainElement;
		MCMain mainAnnotation;
		
		List<ElementInfo> iis = new ArrayList<ElementInfo>();
		Map<String, List<ElementInfo>> groups = new HashMap<String, List<ElementInfo>>();
		List<MCRaw> raws = new ArrayList<MCRaw>();
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
	
	private static class ProcessingException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		
		Element[] e;
		
		public ProcessingException(String message, Element... e) {
			super(message);
			this.e = e;
		}
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
				if (mcmains.size() > 1) {
					for (Element e : mcmains)
						processingEnv.getMessager().printMessage(Kind.ERROR, "@MCMain found in multiple locations.", e);
					return true;
				}
				m.mainElement = (TypeElement)mcmains.iterator().next();
				m.mainAnnotation = m.mainElement.getAnnotation(MCMain.class);

				for (Element e : roundEnv.getElementsAnnotatedWith(MCElement.class)) {
					ElementInfo ii = new ElementInfo();
					ii.element = (TypeElement)e;
					ii.annotation = e.getAnnotation(MCElement.class);
					ii.generateParserClass = processingEnv.getElementUtils().getTypeElement("com.predic8.membrane.core.config.spring." + ii.getParserClassSimpleName()) == null;
					m.iis.add(ii);
					
					if (!m.groups.containsKey(ii.annotation.group()))
						m.groups.put(ii.annotation.group(), new ArrayList<ElementInfo>());
					m.groups.get(ii.annotation.group()).add(ii);
					m.elements.put(ii.element, ii);
					if (m.globals.containsKey(ii.annotation.name()))
						throw new ProcessingException("Duplicate global @MCElement name.", m.globals.get(ii.annotation.name()).element, ii.element);
	
					scan(m, ii);
				}
				
				for (Map.Entry<TypeElement, ChildElementDeclarationInfo> f : m.childElementDeclarations.entrySet()) {
					ChildElementDeclarationInfo cedi = f.getValue();
					ElementInfo ei = m.elements.get(f.getKey());
					
					if (ei != null)
						cedi.elementInfo.add(ei);
					else {
						for (Map.Entry<TypeElement, ElementInfo> e : m.elements.entrySet())
							if (isAssignableFrom(f.getKey(), e.getKey()))
								cedi.elementInfo.add(e.getValue());
					}
					
					if (cedi.elementInfo.size() == 0) {
						processingEnv.getMessager().printMessage(Kind.ERROR, "@MCChildElement references " + f.getKey().getQualifiedName() + ", but there is no @MCElement among it and its subclasses.", f.getKey());
						return true;
					}
				}


				if (mcmains.size() == 0) {
					processingEnv.getMessager().printMessage(Kind.ERROR, "@MCMain but no @MCElement found.", mcmains.iterator().next());
					return true;
				}
				
				for (Element e : roundEnv.getElementsAnnotatedWith(MCRaw.class)) {
					m.raws.add(e.getAnnotation(MCRaw.class));
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

	private boolean isAssignableFrom(TypeElement thiz, TypeElement clazz) {
		if (clazz.equals(thiz))
			return true;
		TypeMirror tm = clazz.getSuperclass();
		if (tm.getKind() == TypeKind.DECLARED)
			if (isAssignableFrom(thiz, (TypeElement) ((DeclaredType) tm).asElement()))
				return true;
		for (TypeMirror tm2 : clazz.getInterfaces())
			if (isAssignableFrom(thiz, (TypeElement) ((DeclaredType) tm2).asElement()))
				return true;
		return false;
	}

	private static final String REQUIRED = "org.springframework.beans.factory.annotation.Required";
	
	private void scan(Model m, AbstractElementInfo ii) {
		scan(m, ii, ii.element);
	}
	
	private void scan(Model m, AbstractElementInfo ii, TypeElement te) {
		TypeMirror superclass = te.getSuperclass();
		if (superclass instanceof DeclaredType)
			scan(m, ii, (TypeElement) ((DeclaredType)superclass).asElement());
		
		for (Element e2 : te.getEnclosedElements()) {
			MCAttribute a = e2.getAnnotation(MCAttribute.class);
			if (a != null) {
				AttributeInfo ai = new AttributeInfo();
				ai.e = (ExecutableElement) e2;
				ai.required = isRequired(e2);
				ii.ais.add(ai);
				ii.hasIdField = ii.hasIdField || ai.getName().equals("id");
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
				
				if (!m.childElementDeclarations.containsKey(cei.typeDeclaration)) {
					ChildElementDeclarationInfo cedi = new ChildElementDeclarationInfo();
					
					m.childElementDeclarations.put(cei.typeDeclaration, cedi);
				}
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
		List<Element> sources = new ArrayList<Element>();
		sources.add(m.mainElement);
		sources.addAll(m.getInterceptorElements());
		
		FileObject o = processingEnv.getFiler().createSourceFile(
				m.mainAnnotation.outputPackage() + ".NamespaceHandlerAutoGenerated",
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
					"package com.predic8.membrane.core.config.spring;\r\n" + 
					"\r\n" + 
					"public class NamespaceHandlerAutoGenerated {\r\n" + 
					"\r\n" + 
					"	public static void registerBeanDefinitionParsers(NamespaceHandler nh) {\r\n");
			for (ElementInfo i : m.iis) {
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

	private void writeXSD(Model m) throws IOException, ProcessingException {
		List<Element> sources = new ArrayList<Element>();
		sources.add(m.mainElement);
		sources.addAll(m.getInterceptorElements());

		FileObject o = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
				m.mainAnnotation.outputPackage(), m.mainAnnotation.outputName(), sources.toArray(new Element[0]));
		BufferedWriter bw = new BufferedWriter(o.openWriter());
		try {
			assembleXSD(m, bw);
		} finally {
			bw.close();
		}
	}

	private void writeParsers(Model m) throws IOException {
		for (ElementInfo ii : m.iis) {
			
			if (!ii.generateParserClass)
				continue;
			
			List<Element> sources = new ArrayList<Element>();
			sources.add(m.mainElement);
			sources.add(ii.element);
			
			String interceptorClassName = ii.element.getQualifiedName().toString();
			
			FileObject o = processingEnv.getFiler().createSourceFile(m.mainAnnotation.outputPackage() + "." + ii.getParserClassSimpleName(),
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
						"package com.predic8.membrane.core.config.spring;\r\n" + 
						"\r\n" + 
						"import org.w3c.dom.Element;\r\n" + 
						"import org.springframework.beans.factory.xml.ParserContext;\r\n" + 
						"import org.springframework.beans.factory.support.BeanDefinitionBuilder;\r\n" + 
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
					if (ai.getName().equals("id"))
						continue;
					bw.write("		setProperty" + (ai.required ? "" : "IfSet") + "(\"" + ai.getName() + "\", element, builder" + (ai.isEnum() ? ", true" : "") + ");\r\n");
					if (ai.getName().equals("name"))
						bw.write("		element.removeAttribute(\"name\");");
				}
				for (ChildElementInfo cei : ii.ceis)
					if (cei.list)
						bw.write("		builder.addPropertyValue(\"" + cei.propertyName + "\", new java.util.ArrayList<Object>());\r\n");
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

	private void assembleXSD(Model m, BufferedWriter bw) throws IOException, ProcessingException {
		String xsd = m.mainAnnotation.xsd(); 
		xsd = xsd.replace("${declarations}", assembleDeclarations(m));
		for (String group : m.groups.keySet()) {
			xsd = xsd.replace("${" + group + "Declarations}", "");
			xsd = xsd.replace("${" + group + "References}", assembleInterceptorReferences(m, group));
		}
		xsd = xsd.replace("${raw}", assembleRaw(m));
		bw.append(xsd);
	}

	private String assembleRaw(Model m) {
		StringWriter raws = new StringWriter();
		for (MCRaw raw : m.raws) {
			raws.append(raw.xsd());
		}
		return raws.toString();
	}

	private String assembleDeclarations(Model m) throws ProcessingException {
		StringWriter declarations = new StringWriter();
		for (ElementInfo i : m.elements.values()) {
			declarations.append(assembleElementDeclaration(m, i));
		}
		return declarations.toString();
	}

	private CharSequence assembleElementDeclaration(Model m, ElementInfo i) throws ProcessingException {
		String xsd;
		if (i.annotation.xsd().length() == 0) {
			if (i.annotation.mixed()) {
				throw new ProcessingException(
						"@MCElement(..., mixed=true) also requires (..., mixed=true, xsd=\"...\").",
						i.element);
			}
			if (i.ais.size() > 0 || i.ceis.size() > 0) {
				xsd = assembleElementInfo(m, i);
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

	private String assembleElementInfo(Model m, AbstractElementInfo i) {
		StringBuilder xsd = new StringBuilder();
		xsd.append("<xsd:sequence>\r\n");
		for (ChildElementInfo cei : i.ceis) {
			xsd.append("<xsd:choice" + (cei.required ? " minOccurs=\"1\"" : " minOccurs=\"0\"") + (cei.list ? " maxOccurs=\"unbounded\"" : "") + ">\r\n");
			for (ElementInfo ei : m.childElementDeclarations.get(cei.typeDeclaration).elementInfo)
				xsd.append("<xsd:element ref=\"" + ei.annotation.name() + "\" />\r\n");
			xsd.append("</xsd:choice>\r\n");
		}
		xsd.append("</xsd:sequence>\r\n");
		for (AttributeInfo ai : i.ais)
			if (!ai.getName().equals("id"))
				xsd.append(assembleAttributeDeclaration(ai));
		return xsd.toString();
	}

	private String assembleAttributeDeclaration(AttributeInfo ai) {
		// TODO: default value
		return "<xsd:attribute name=\"" + ai.getName() + "\" type=\"" + ai.getXSDType() + "\" "
				+ (ai.required ? "use=\"required\"" : "") + " />\r\n";
	}

	private String assembleInterceptorReferences(Model m, String group) {
		StringWriter interceptorReferences = new StringWriter();
		for (ElementInfo i : m.groups.get(group)) {
			interceptorReferences.append("<xsd:element ref=\"" + i.annotation.name() + "\" />\r\n");
		}
		return interceptorReferences.toString();
	}
}