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
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes(value = { "com.predic8.membrane.annot.*" })
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class SpringConfigurationXSDGeneratingAnnotationProcessor extends AbstractProcessor {
	
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		try {
			if (annotations.size() > 0) { // somehow we get called twice in the javac run from Maven

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
				MCMain mcmain = mcmains.iterator().next().getAnnotation(MCMain.class);

				List<MCInterceptor> mcinterceptors = new ArrayList<MCInterceptor>();
				for (Element e : roundEnv.getElementsAnnotatedWith(MCInterceptor.class))
					mcinterceptors.add(e.getAnnotation(MCInterceptor.class));

				if (mcmains.size() == 0) {
					processingEnv.getMessager().printMessage(Kind.ERROR, "@MCMain but no @MCInterceptor found.", mcmains.iterator().next());
					return true;
				}
				
				List<Element> sources = new ArrayList<Element>();
				sources.add(mcmains.iterator().next());
				sources.addAll(roundEnv.getElementsAnnotatedWith(MCInterceptor.class));

				FileObject o = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
						mcmain.outputPackage(), mcmain.outputName(), sources.toArray(new Element[0]));
				BufferedWriter bw = new BufferedWriter(o.openWriter());
				try {
					assembleXSD(mcmain, mcinterceptors, bw);
				} finally {
					bw.close();
				}
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
		}
	}

	private void assembleXSD(MCMain mcmain, List<MCInterceptor> mcinterceptors, BufferedWriter bw) throws IOException {
		bw.append(
				mcmain.xsd()
				.replace("${interceptorDeclarations}", assembleInterceptorDeclarations(mcinterceptors)));
	}

	private String assembleInterceptorDeclarations(List<MCInterceptor> mcinterceptors) {
		StringWriter interceptorDeclarations = new StringWriter();
		for (MCInterceptor i : mcinterceptors) {
			if (i.xsd().length() == 0) {
				if (i.mixed())
					throw new RuntimeException("@MCInterceptor(..., mixed=true) also requires (..., mixed=true, xsd=\"...\").");
				interceptorDeclarations.append("<xsd:element name=\""+ i.name() + "\" type=\"EmptyElementType\" />\r\n");
			} else {
				interceptorDeclarations.append(
						"<xsd:element name=\""+ i.name() +"\">\r\n" + 
						"	<xsd:complexType>\r\n" + 
						"		<xsd:complexContent " + (i.mixed() ? "mixed=\"true\"" : "") + ">\r\n" + 
						"			<xsd:extension base=\"beans:identifiedType\">\r\n" + 
						i.xsd() +
						"			</xsd:extension>\r\n" + 
						"		</xsd:complexContent>\r\n" + 
						"	</xsd:complexType>\r\n" + 
						"</xsd:element>\r\n");
			}
		}
		return interceptorDeclarations.toString();
	}
}