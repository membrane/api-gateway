package com.predic8.membrane.annot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import com.predic8.membrane.annot.generator.Parsers;
import com.predic8.membrane.annot.generator.Schemas;
import com.predic8.membrane.annot.model.AbstractElementInfo;
import com.predic8.membrane.annot.model.AttributeInfo;
import com.predic8.membrane.annot.model.ChildElementDeclarationInfo;
import com.predic8.membrane.annot.model.ChildElementInfo;
import com.predic8.membrane.annot.model.ElementInfo;
import com.predic8.membrane.annot.model.MainInfo;
import com.predic8.membrane.annot.model.Model;
import com.predic8.membrane.annot.model.TextContentInfo;

@SupportedAnnotationTypes(value = { "com.predic8.membrane.annot.*" })
public class SpringConfigurationXSDGeneratingAnnotationProcessor extends AbstractProcessor {
	
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
					main.setElement((TypeElement)element);
					main.setAnnotation(element.getAnnotation(MCMain.class));
					m.getMains().add(main);
				}

				for (Element e : roundEnv.getElementsAnnotatedWith(MCElement.class)) {
					ElementInfo ii = new ElementInfo();
					ii.setElement((TypeElement)e);
					ii.setAnnotation(e.getAnnotation(MCElement.class));
					ii.setGenerateParserClass(processingEnv.getElementUtils().getTypeElement(ii.getClassName(m)) == null);
					MainInfo main = ii.getMain(m);
					main.getIis().add(ii);
					
					if (!main.getGroups().containsKey(ii.getAnnotation().group()))
						main.getGroups().put(ii.getAnnotation().group(), new ArrayList<ElementInfo>());
					main.getGroups().get(ii.getAnnotation().group()).add(ii);
					main.getElements().put(ii.getElement(), ii);
					if (main.getGlobals().containsKey(ii.getAnnotation().name()))
						throw new ProcessingException("Duplicate global @MCElement name.", main.getGlobals().get(ii.getAnnotation().name()).getElement(), ii.getElement());
	
					scan(m, main, ii);
					
					if (ii.getTci() != null && !ii.getAnnotation().mixed())
						throw new ProcessingException("@MCTextContent requires @MCElement(..., mixed=true) on the class.", ii.getElement());
					if (ii.getTci() == null && ii.getAnnotation().mixed())
						throw new ProcessingException("@MCElement(..., mixed=true) requires @MCTextContent on a property.", ii.getElement());
				}
				
				for (MainInfo main : m.getMains()) {
					
					for (Map.Entry<TypeElement, ChildElementDeclarationInfo> f : main.getChildElementDeclarations().entrySet()) {
						ChildElementDeclarationInfo cedi = f.getValue();
						ElementInfo ei = main.getElements().get(f.getKey());

						if (ei != null)
							cedi.getElementInfo().add(ei);
						else {
							for (Map.Entry<TypeElement, ElementInfo> e : main.getElements().entrySet())
								if (processingEnv.getTypeUtils().isAssignable(e.getKey().asType(), f.getKey().asType()))
									cedi.getElementInfo().add(e.getValue());
						}

						if (cedi.getElementInfo().size() == 0 && cedi.isRaiseErrorWhenNoSpecimen()) {
							processingEnv.getMessager().printMessage(Kind.ERROR, "@MCChildElement references " + f.getKey().getQualifiedName() + ", but there is no @MCElement among it and its subclasses.", f.getKey());
							return true;
						}
					}
				}


				if (mcmains.size() == 0) {
					processingEnv.getMessager().printMessage(Kind.ERROR, "@MCMain but no @MCElement found.", mcmains.iterator().next());
					return true;
				}

				new Schemas(processingEnv).writeXSD(m);
				new Parsers(processingEnv).writeParsers(m);
				new Parsers(processingEnv).writeParserDefinitior(m);
			}

			return true;
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ProcessingException e1) {
			for (int i = 0; i < e1.getElements().length; i++)
				processingEnv.getMessager().printMessage(Kind.ERROR, i == 0 ? e1.getMessage() : "also here", e1.getElements()[i]);
			return true;
		}
	}

	private static final String REQUIRED = "org.springframework.beans.factory.annotation.Required";
	
	private void scan(Model m, MainInfo main, AbstractElementInfo ii) {
		scan(m, main, ii, ii.getElement());
	}
	
	private void scan(Model m, MainInfo main, AbstractElementInfo ii, TypeElement te) {
		TypeMirror superclass = te.getSuperclass();
		if (superclass instanceof DeclaredType)
			scan(m, main, ii, (TypeElement) ((DeclaredType)superclass).asElement());
		
		for (Element e2 : te.getEnclosedElements()) {
			MCAttribute a = e2.getAnnotation(MCAttribute.class);
			if (a != null) {
				AttributeInfo ai = new AttributeInfo();
				ai.setAnnotation(a);
				ai.setE((ExecutableElement) e2);
				ai.setRequired(isRequired(e2));
				ii.getAis().add(ai);
				ii.setHasIdField(ii.isHasIdField() || ai.getXMLName().equals("id"));
			}
			MCChildElement b = e2.getAnnotation(MCChildElement.class);
			if (b != null) {
				ChildElementInfo cei = new ChildElementInfo();
				cei.setAnnotation(b);
				cei.setE((ExecutableElement) e2);
				TypeMirror setterArgType = cei.getE().getParameters().get(0).asType();
				cei.setTypeDeclaration((TypeElement) ((DeclaredType) setterArgType).asElement());
				cei.setPropertyName(AnnotUtils.dejavaify(e2.getSimpleName().toString().substring(3)));
				cei.setRequired(isRequired(e2));
				ii.getCeis().add(cei);
				
				// unwrap "java.util.List<?>" and "java.util.Collection<?>"
				if (cei.getTypeDeclaration().getQualifiedName().toString().startsWith("java.util.List") ||
						cei.getTypeDeclaration().getQualifiedName().toString().startsWith("java.util.Collection")) {
					cei.setTypeDeclaration((TypeElement) ((DeclaredType) ((DeclaredType) setterArgType).getTypeArguments().get(0)).asElement());
					cei.setList(true);
				}
				
				if (!main.getChildElementDeclarations().containsKey(cei.getTypeDeclaration())) {
					ChildElementDeclarationInfo cedi = new ChildElementDeclarationInfo();
					cedi.setRaiseErrorWhenNoSpecimen(!cei.getAnnotation().allowForeign());
					
					main.getChildElementDeclarations().put(cei.getTypeDeclaration(), cedi);
				} else {
					ChildElementDeclarationInfo cedi = main.getChildElementDeclarations().get(cei.getTypeDeclaration());
					cedi.setRaiseErrorWhenNoSpecimen(cedi.isRaiseErrorWhenNoSpecimen() || !cei.getAnnotation().allowForeign());
				}
			}
			MCTextContent c = e2.getAnnotation(MCTextContent.class);
			if (c != null) {
				TextContentInfo tci = new TextContentInfo();
				tci.setPropertyName(AnnotUtils.dejavaify(e2.getSimpleName().toString().substring(3)));
				ii.setTci(tci);
			}
		}
		Collections.sort(ii.getCeis());
	}

	private boolean isRequired(Element e2) {
		for (AnnotationMirror am : e2.getAnnotationMirrors())
			if (((TypeElement)am.getAnnotationType().asElement()).getQualifiedName().toString().equals(REQUIRED))
				return true;
		return false;
	}

}