package com.predic8.membrane.annot;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
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
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes(value = { "com.predic8.membrane.annot.*" })
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class SpringConfigurationXSDGeneratingAnnotationProcessor extends AbstractProcessor {
	
	private class AttributeInfo {
		ExecutableElement e;
		boolean required;
		
		public String getName() {
			String s = e.getSimpleName().toString();
			if (!s.substring(0, 3).equals("set"))
				throw new ProcessingException("Setter method name is supposed to start with 'set'.", e);
			s = s.substring(3);
			s = Character.toLowerCase(s.charAt(0)) + s.substring(1);
			return s;
		}

		public String getXSDType() {
			if (e.getParameters().size() != 1)
				throw new ProcessingException("Setter is supposed to have 1 parameter.", e);
			VariableElement ve = e.getParameters().get(0);
			switch (ve.asType().getKind()) {
			case INT:
				return "xsd:int";
			case LONG:
				return "xsd:long";
			case BOOLEAN:
				return "xsd:boolean";
			case DECLARED:
				TypeElement e = (TypeElement) processingEnv.getTypeUtils().asElement(ve.asType());
				if (e.getQualifiedName().toString().equals("java.lang.String"))
					return "xsd:string";
				throw new RuntimeException("Not implemented: XSD type for " + e.getQualifiedName());
			default:
				throw new RuntimeException("Not implemented: XSD type for " + ve.asType().getKind().toString());
			}
		}
	}
	
	private static class InterceptorInfo {
		TypeElement element;
		MCInterceptor annotation;
		
		List<AttributeInfo> ais = new ArrayList<AttributeInfo>();
	}
	
	private static class Model {
		TypeElement mainElement;
		MCMain mainAnnotation;
		
		List<InterceptorInfo> iis = new ArrayList<InterceptorInfo>();
		
		List<Element> getInterceptorElements() {
			ArrayList<Element> res = new ArrayList<Element>(iis.size());
			for (InterceptorInfo ii : iis)
				res.add(ii.element);
			return res;
		}
	}
	
	private static class ProcessingException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		
		Element e;
		
		public ProcessingException(String message, Element e) {
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

				for (Element e : roundEnv.getElementsAnnotatedWith(MCInterceptor.class)) {
					InterceptorInfo ii = new InterceptorInfo();
					ii.element = (TypeElement)e;
					ii.annotation = e.getAnnotation(MCInterceptor.class);
					m.iis.add(ii);
					
					for (Element e2 : e.getEnclosedElements()) {
						MCAttribute a = e2.getAnnotation(MCAttribute.class);
						if (a == null)
							continue;
						AttributeInfo ai = new AttributeInfo();
						ai.e = (ExecutableElement) e2;
						for (AnnotationMirror am : e2.getAnnotationMirrors())
							if (((TypeElement)am.getAnnotationType().asElement()).getQualifiedName().toString().equals("org.springframework.beans.factory.annotation.Required"))
								ai.required = true;
						ii.ais.add(ai);
					}
				}

				if (mcmains.size() == 0) {
					processingEnv.getMessager().printMessage(Kind.ERROR, "@MCMain but no @MCInterceptor found.", mcmains.iterator().next());
					return true;
				}
				

				writeXSD(m);
				writeParsers(m);
				writeParserDefinitior(m);
			}

			/*
			for (TypeElement element : annotations) {
				System.err.println(element.getQualifiedName());
				for (Element e : roundEnv.getElementsAnnotatedWith(element))
					System.err.println(" at " + e.getSimpleName());
			}
			*/
			return true;
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ProcessingException e1) {
			processingEnv.getMessager().printMessage(Kind.ERROR, e1.getMessage(), e1.e);
			return true;
		}
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
			for (InterceptorInfo i : m.iis) {
				String parserClassName = javaify(i.annotation.name() + "InterceptorParser");
				bw.write("		nh.registerBeanDefinitionParser2(\"" + i.annotation.name() + "\", new " + parserClassName + "());\r\n");
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
		for (InterceptorInfo ii : m.iis) {
			
			if (ii.annotation.xsd().length() != 0)
				continue;
			
			List<Element> sources = new ArrayList<Element>();
			sources.add(m.mainElement);
			sources.add(ii.element);
			
			String interceptorClassName = ii.element.getQualifiedName().toString();
			String parserClassName = javaify(ii.annotation.name() + "InterceptorParser");
			
			FileObject o = processingEnv.getFiler().createSourceFile(m.mainAnnotation.outputPackage() + "." + parserClassName,
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
						"\r\n" + 
						"public class " + parserClassName + " extends AbstractParser {\r\n" + 
						"\r\n" + 
						"	protected Class<?> getBeanClass(Element element) {\r\n" + 
						"		return " + interceptorClassName + ".class;\r\n" + 
						"	}\r\n");
				if (ii.ais.size() > 0) {
					bw.write("	@Override\r\n" + 
							"	protected void doParse(Element element, org.springframework.beans.factory.support.BeanDefinitionBuilder builder) {\r\n");
					for (AttributeInfo ai : ii.ais)
						bw.write("		setProperty" + (ai.required ? "" : "IfSet") + "(\"" + ai.getName() + "\", element, builder);\r\n");
					bw.write(
							"	}\r\n" + 
							"");
				}
				bw.write(
						"}\r\n" + 
						"");
			} finally {
				bw.close();
			}
		}

	}

	private String javaify(String s) {
		StringBuilder sb = new StringBuilder(s);
		sb.replace(0, 1, "" + Character.toUpperCase(s.charAt(0)));
		return sb.toString();
	}
	
	private void assembleXSD(Model m, BufferedWriter bw) throws IOException, ProcessingException {
		bw.append(
				m.mainAnnotation.xsd()
				.replace("${interceptorDeclarations}", assembleInterceptorDeclarations(m))
				.replace("${interceptorReferences}", assembleInterceptorReferences(m)));
	}

	private String assembleInterceptorDeclarations(Model m) throws ProcessingException {
		StringWriter interceptorDeclarations = new StringWriter();
		for (InterceptorInfo i : m.iis) {
			interceptorDeclarations.append(assembleInterceptorDeclaration(m, i));
		}
		return interceptorDeclarations.toString();
	}

	private CharSequence assembleInterceptorDeclaration(Model m, InterceptorInfo i) throws ProcessingException {
		String xsd;
		if (i.annotation.xsd().length() == 0) {
			if (i.annotation.mixed()) {
				throw new ProcessingException(
						"@MCInterceptor(..., mixed=true) also requires (..., mixed=true, xsd=\"...\").",
						i.element);
			}
			if (i.ais.size() > 0) {
				xsd = "<xsd:sequence />\r\n";
				for (AttributeInfo ai : i.ais) {
					xsd += assembleAttributeDeclaration(ai);
				}
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

	private String assembleAttributeDeclaration(AttributeInfo ai) {
		// TODO: default value
		return "<xsd:attribute name=\"" + ai.getName() + "\" type=\"" + ai.getXSDType() + "\" "
				+ (ai.required ? "use=\"required\"" : "") + " />\r\n";
	}

	private String assembleInterceptorReferences(Model m) {
		StringWriter interceptorReferences = new StringWriter();
		for (InterceptorInfo i : m.iis) {
			interceptorReferences.append("<xsd:element ref=\"" + i.annotation.name() + "\" />\r\n");
		}
		return interceptorReferences.toString();
	}
}