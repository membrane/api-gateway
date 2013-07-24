package com.predic8.membrane.annot.model;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.model.doc.Doc;

/**
 * Common behavior of Javadoc handling for {@link MCAttribute}, {@link MCElement}, etc.
 */
public abstract class AbstractJavadocedInfo {
	private Element docedE;
	private boolean docGenerated;
	private Doc doc;
	
	
	public void setDocedE(Element docedE) {
		this.docedE = docedE;
	}
	
	public Element getDocedE() {
		return docedE;
	}

	public Doc getDoc(ProcessingEnvironment processingEnv) {
		if (docGenerated)
			return doc;
		
		docGenerated = true;
		
		String javadoc = processingEnv.getElementUtils().getDocComment(getDocedE());
		if (javadoc == null)
			return null;
		
		return doc = new Doc(processingEnv, javadoc, getDocedE());
	}

}
