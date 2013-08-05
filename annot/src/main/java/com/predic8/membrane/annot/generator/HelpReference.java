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

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import com.predic8.membrane.annot.model.AbstractJavadocedInfo;
import com.predic8.membrane.annot.model.AttributeInfo;
import com.predic8.membrane.annot.model.ChildElementDeclarationInfo;
import com.predic8.membrane.annot.model.ChildElementInfo;
import com.predic8.membrane.annot.model.ElementInfo;
import com.predic8.membrane.annot.model.MainInfo;
import com.predic8.membrane.annot.model.Model;
import com.predic8.membrane.annot.model.OtherAttributesInfo;
import com.predic8.membrane.annot.model.doc.Doc;

public class HelpReference {

	private final ProcessingEnvironment processingEnv;

	private XMLStreamWriter xew;
	private StringWriter sw;

	public HelpReference(ProcessingEnvironment processingEnv) {
		this.processingEnv = processingEnv;
	}

	public void writeHelp(Model m) {
		try {
			String path = System.getenv("MEMBRANE_GENERATE_DOC_DIR");
			if (path == null)
				return;
			path = path.replace("%VERSION%", "4.0");

			sw = new StringWriter();
			XMLOutputFactory output = XMLOutputFactory.newInstance();
			xew = output.createXMLStreamWriter(sw);
			xew.writeStartDocument();
			handle(m);
			xew.writeEndDocument();
			
			// indent
	        TransformerFactory factory = TransformerFactory.newInstance();
	        Transformer transformer = factory.newTransformer();
	        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
	        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
	        transformer.transform(new StreamSource(new StringReader(sw.toString())), new StreamResult(new File(path + "/" + getFileName(m) + ".xml")));

			xew = null;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
	}

	private String getFileName(Model m) {
		ArrayList<String> packages = new ArrayList<String>();
		for (MainInfo mi : m.getMains())
			packages.add(mi.getAnnotation().outputPackage());
		Collections.sort(packages);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < packages.size(); i++) {
			if (i > 0)
				sb.append("-");
			sb.append(packages.get(i));
		}
		return sb.toString();
	}

	private void handle(Model m) throws XMLStreamException {
		xew.writeStartElement("namespaces");
		for (MainInfo main : m.getMains())
			handle(m, main);
		xew.writeEndElement();
	}

	private void handle(Model m, MainInfo main) throws XMLStreamException {
		xew.writeStartElement("namespace");
		xew.writeAttribute("package", main.getAnnotation().outputPackage());
		xew.writeAttribute("targetNamespace", main.getAnnotation().targetNamespace());
		Collections.sort(main.getIis(), new Comparator<ElementInfo>() {
			@Override
			public int compare(ElementInfo o1, ElementInfo o2) {
				int res = o1.getAnnotation().name().compareTo(o2.getAnnotation().name());
				if (res == 0)
					res = o1.getElement().getQualifiedName().toString().compareTo(o2.getElement().getQualifiedName().toString());
				return res;
			}
		});
		for (ElementInfo ei : main.getIis())
			handle(m, main, ei);
		xew.writeEndElement();
	}

	private void handle(Model m, MainInfo main, ElementInfo ei) throws XMLStreamException {
		xew.writeStartElement("element");
		xew.writeAttribute("name", ei.getAnnotation().name());
		if (ei.getAnnotation().mixed())
			xew.writeAttribute("mixed", "true");
		xew.writeAttribute("topLevel", Boolean.toString(ei.getAnnotation().topLevel()));
		xew.writeAttribute("id", ei.getId());
		if (!ei.getAnnotation().topLevel()) {
			String primaryParentId = getPrimaryParentId(m, main, ei);
			if (primaryParentId != null)
				xew.writeAttribute("primaryParentId", primaryParentId);
		}
		
		handleDoc(ei);
		
		List<AttributeInfo> ais = ei.getAis();
		Collections.sort(ais, new Comparator<AttributeInfo>() {

			@Override
			public int compare(AttributeInfo o1, AttributeInfo o2) {
				return o1.getXMLName().compareTo(o2.getXMLName());
			}
			
		});
		OtherAttributesInfo oai = ei.getOai();
		
		if (ais.size() > 0 && ais.get(0).getXMLName().equals("id"))
			ais.remove(0);
		
		if (ais.size() > 0 || oai != null) {
			xew.writeStartElement("attributes");
			for (AttributeInfo ai : ais)
				handle(ai);
			if (oai != null) {
				xew.writeStartElement("any");
				handleDoc(oai);
				xew.writeEndElement();
			}
			xew.writeEndElement();
		}
		
		List<ChildElementInfo> ceis = ei.getCeis();
		if (ceis.size() > 0) {
			xew.writeStartElement("children");
			for (ChildElementInfo cei : ceis) 
				handle(m, main, cei);
			xew.writeEndElement();
		}
		
		xew.writeEndElement();
	}

	private String getPrimaryParentId(Model m, MainInfo mi, ElementInfo ei) {
		// choose a random parent (TODO: choose a better one)
		Set<ElementInfo> possibleParents = new HashSet<ElementInfo>();
		for (Map.Entry<TypeElement, ChildElementDeclarationInfo> e : mi.getChildElementDeclarations().entrySet())
			if (e.getValue().getElementInfo().contains(ei)) {
				for (ChildElementInfo usedBy : e.getValue().getUsedBy()) {
					ElementInfo e2 = mi.getElements().get(usedBy.getEi().getElement());
					if (e2 != null)
						possibleParents.add(e2);
				}
			}
		for (ElementInfo ei2 : possibleParents)
			if (ei2.getAnnotation().topLevel())
				return ei2.getId();
		possibleParents.remove(ei);
		if (possibleParents.size() > 0)
			return possibleParents.iterator().next().getId();
		return null;
	}

	/*
	private int getId(String xsdTypeName) {
		if (ids.containsKey(xsdTypeName))
			return ids.get(xsdTypeName);
		int id = Math.abs(xsdTypeName.hashCode());
		if (idsReverse.containsKey(id))
			throw new ProcessingException("ID-assigning algorithm failed (two XSD types got the same ID)");
		ids.put(xsdTypeName, id);
		idsReverse.put(id, xsdTypeName);
		return id;
	}
	*/

	private void handle(Model m, MainInfo main, ChildElementInfo cei) throws XMLStreamException {
		xew.writeStartElement("child");
		xew.writeAttribute("min", cei.isRequired() ? "1" : "0");
		xew.writeAttribute("max", cei.isList() ? "unbounded" : "1");
		
		handleDoc(cei);
		
		SortedSet<String> possibilities = new TreeSet<String>();
		for (ElementInfo ei : main.getChildElementDeclarations().get(cei.getTypeDeclaration()).getElementInfo()) {
			possibilities.add(ei.getId());
		}
		for (String id : possibilities) {
			xew.writeStartElement("possibility");
			xew.writeAttribute("refId", id);
			xew.writeEndElement();
		}
		
		if (cei.getAnnotation().allowForeign()) {
			xew.writeStartElement("possibility");
			xew.writeAttribute("foreign", "true");
			xew.writeEndElement();
		}
		
		xew.writeEndElement();
		
	}

	private void handle(AttributeInfo ai) throws XMLStreamException {
		if (ai.getXMLName().equals("id"))
			return;
		
		xew.writeStartElement("attribute");
		xew.writeAttribute("name", ai.getXMLName());
		xew.writeAttribute("required", Boolean.toString(ai.isRequired()));
		handleDoc(ai);
		xew.writeEndElement();
	}

	private void handleDoc(AbstractJavadocedInfo info) throws XMLStreamException {
		Doc doc = info.getDoc(processingEnv);
		if (doc == null)
			return;
		
		xew.writeStartElement("documentation");
		
		for (Doc.Entry e : doc.getEntries())
			handleDoc(e);
		
		xew.writeEndElement();
	}
	
	private void handleDoc(Doc.Entry e) throws XMLStreamException {
		xew.writeCharacters("");
		xew.flush();
		
		sw.append(e.getValueAsXMLSnippet(true));
	}

}
