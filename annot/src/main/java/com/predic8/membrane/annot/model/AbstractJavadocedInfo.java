/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
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
