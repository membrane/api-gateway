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
package com.predic8.membrane.annot.generator;

import com.predic8.membrane.annot.ProcessingException;
import com.predic8.membrane.annot.model.*;
import com.predic8.membrane.annot.model.doc.Doc;
import com.predic8.membrane.annot.model.doc.Doc.Entry;

import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class ResourceInfo {

	private final ProcessingEnvironment processingEnv;

	public ResourceInfo(ProcessingEnvironment processingEnv) {
		this.processingEnv = processingEnv;
	}

	public void write(Model m) throws IOException {
		try {

			for (MainInfo main : m.getMains()) {
                List<Element> sources = new ArrayList<>(m.getResources());

				FileObject o = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
						main.getAnnotation().outputPackage(), "resources.txt", sources.toArray(new Element[0]));
                try (BufferedWriter bw = new BufferedWriter(o.openWriter())) {
					for (TypeElement resource : m.getResources()) {
						bw.write(resource.getQualifiedName() + "\n");
					}
                }
			}
		} catch (FilerException e) {
			if (e.getMessage().contains("Source file already created"))
				return;
			throw e;
		}
	}

}
