package com.predic8.membrane.annot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.predic8.membrane.annot.generator.Parsers;
import com.predic8.membrane.annot.generator.Schemas;
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

	private static final String CACHE_FILE_FORMAT_VERSION = "1";
	
	private void log(String message) {
		//processingEnv.getMessager().printMessage(Kind.NOTE, message);  // for Eclipse
		//System.out.println(message); // for Maven command line
	}
	
	@SuppressWarnings("unchecked")
	private void read() {
		if (cache != null)
			return;
		
		cache = new HashMap<Class<? extends Annotation>, HashSet<Element>>();
		
		try {
			FileObject o = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/membrane.cache");
			BufferedReader r = new BufferedReader(o.openReader(false));
			try {
				if (!CACHE_FILE_FORMAT_VERSION.equals(r.readLine()))
					return;
				HashSet<Element> currentSet = null;
				Class<? extends Annotation> annotationClass = null;
				while (true) {
					String line = r.readLine();
					if (line == null)
						break;
					if (line.startsWith(" ")) {
						line = line.substring(1);
						TypeElement element = null;
						try {
							element = processingEnv.getElementUtils().getTypeElement(line);
						} catch (RuntimeException e) {
							// do nothing (Eclipse)
						}
						if (element != null) {
							if (element.getAnnotation(annotationClass) != null)
								currentSet.add(element);
						}
					} else {
						try {
							annotationClass = (Class<? extends Annotation>) getClass().getClassLoader().loadClass(line);
						} catch (ClassNotFoundException e) {
							throw new RuntimeException(e);
						}
						currentSet = new HashSet<Element>();
						cache.put(annotationClass, currentSet);
					}
				}
			} finally {
				r.close();
			}
		} catch (FileNotFoundException e) {
			// do nothing (Maven)
		} catch (IOException e) {
			// do nothing (Eclipse)
		}

		for (Set<Element> e : cache.values()) {
			String status = "read " + e.size();
			log(status);
		}

	}
	
	private void write() {
		try {
			FileObject o = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/membrane.cache");
			BufferedWriter bw = new BufferedWriter(o.openWriter());
			try {
				bw.write("1\n");

				for (Map.Entry<Class<? extends Annotation>, HashSet<Element>> e : cache.entrySet()) {
					bw.write(e.getKey().getName());
					bw.write("\n");
					for (Element f : e.getValue()) {
						bw.write(" ");
						bw.write(((TypeElement)f).getQualifiedName().toString());
						bw.write("\n");
					}
				}
			} finally {
				bw.close();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private HashMap<Class<? extends Annotation>, HashSet<Element>> cache;
	
	private Set<? extends Element> getCachedElementsAnnotatedWith(RoundEnvironment roundEnv, Class<? extends Annotation> annotation) {
		//FileObject o = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "META-INF", "membrane.cache");
		if (cache == null)
			read();
		
		HashSet<Element> result = cache.get(annotation);
		if (result == null) {
			// update cache
			cache.put(annotation, result = new HashSet<Element>(roundEnv.getElementsAnnotatedWith(annotation)));
		} else {
			for (Element e : roundEnv.getElementsAnnotatedWith(annotation)) {
				result.remove(e);
				result.add(e);
			}
		}
		
		return result;
	}
	
	boolean done;
	
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		// An instance is create per compiler call and not kept for the next incremental compilation.
		//
		// Use "roundEnv.getRootElements()" to get root elements (=classes) changed since last round.
		// Use "processingEnv.getElementUtils().getTypeElement(className)" to get the TypeElement of any class (changed or not).
		// "annotations.size()" equals the number of our annotations found in the "roundEnv.getRootElement()" classes.
		//
		// * rounds are repeated until nothing is filed (=created) any more
		// * resources (and java files?) may only be filed in one round
		// * in the last round, "roundEnv.processingOver()" is true
		try {
			
			String status = "process() a=" + annotations.size() + 
					" r=" + roundEnv.getRootElements().size() + 
					" h=" + hashCode() +
					(roundEnv.processingOver() ? " processing-over" : " ");
			log(status);
			
			read();
			if (roundEnv.processingOver())
				write();
			
			if (annotations.size() > 0) { // a class with one of our annotation needs to be compiled
				
				status = "working with " + getCachedElementsAnnotatedWith(roundEnv, MCMain.class).size() + " and " + getCachedElementsAnnotatedWith(roundEnv, MCElement.class).size();
				log(status);

				
				
				Model m = new Model();
				
				Set<? extends Element> mcmains = getCachedElementsAnnotatedWith(roundEnv, MCMain.class);
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

				for (Element e : getCachedElementsAnnotatedWith(roundEnv, MCElement.class)) {
					ElementInfo ii = new ElementInfo();
					ii.setElement((TypeElement)e);
					ii.setAnnotation(e.getAnnotation(MCElement.class));
					ii.setGenerateParserClass(ii.getAnnotation().generateParserClass());
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
						
						for (ElementInfo ei2 : cedi.getElementInfo())
							ei2.addUsedBy(f.getValue());

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
	
	private void scan(Model m, MainInfo main, ElementInfo ii) {
		scan(m, main, ii, ii.getElement());
	}
	
	private void scan(Model m, MainInfo main, ElementInfo ii, TypeElement te) {
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
				cei.setEi(ii);
				cei.setAnnotation(b);
				cei.setE((ExecutableElement) e2);
				TypeMirror setterArgType = cei.getE().getParameters().get(0).asType();
				if (!(setterArgType instanceof DeclaredType))
					throw new ProcessingException("Setter argument must be of an @MCElement-annotated type.", cei.getE().getParameters().get(0));
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
				
				ChildElementDeclarationInfo cedi;
				if (!main.getChildElementDeclarations().containsKey(cei.getTypeDeclaration())) {
					cedi = new ChildElementDeclarationInfo();
					cedi.setTarget(cei.getTypeDeclaration());
					cedi.setRaiseErrorWhenNoSpecimen(!cei.getAnnotation().allowForeign());
					
					main.getChildElementDeclarations().put(cei.getTypeDeclaration(), cedi);
				} else {
					cedi = main.getChildElementDeclarations().get(cei.getTypeDeclaration());
					cedi.setRaiseErrorWhenNoSpecimen(cedi.isRaiseErrorWhenNoSpecimen() || !cei.getAnnotation().allowForeign());
				}
				cedi.addUsedBy(cei);
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