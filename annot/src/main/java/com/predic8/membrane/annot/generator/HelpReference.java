package com.predic8.membrane.annot.generator;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import com.predic8.membrane.annot.model.AttributeInfo;
import com.predic8.membrane.annot.model.ChildElementInfo;
import com.predic8.membrane.annot.model.ElementInfo;
import com.predic8.membrane.annot.model.MainInfo;
import com.predic8.membrane.annot.model.Model;
import com.predic8.membrane.annot.model.doc.Doc;

public class HelpReference {

	private final ProcessingEnvironment processingEnv;

	private XMLStreamWriter xew;

	public HelpReference(ProcessingEnvironment processingEnv) {
		this.processingEnv = processingEnv;
	}

	public void writeHelp(Model m) {
		try {
			String path = System.getenv("MEMBRANE_GENERATE_DOC_DIR");
			if (path == null)
				return;
			path.replace("%VERSION%", "4.0");

			// serialize
	        StringWriter sw = new StringWriter();
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
		System.err.println(sb.toString());
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
				return o1.getAnnotation().name().compareTo(o2.getAnnotation().name());
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
		xew.writeAttribute("id", "element-" + getId(ei.getXSDTypeName(m)));
		
		handleDoc(ei.getElement());
		
		List<AttributeInfo> ais = ei.getAis();
		if (ais.size() > 0 && ais.get(0).getXMLName().equals("id"))
			ais.remove(0);
		if (ais.size() > 0) {
			xew.writeStartElement("attributes");
			for (AttributeInfo ai : ais)
				handle(ai);
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

	private HashMap<String, Integer> ids = new HashMap<String, Integer>();
	
	private int getId(String xsdTypeName) {
		if (ids.containsKey(xsdTypeName))
			return ids.get(xsdTypeName);
		int id = ids.size();
		ids.put(xsdTypeName, id);
		return id;
	}

	private void handle(Model m, MainInfo main, ChildElementInfo cei) throws XMLStreamException {
		xew.writeStartElement("child");
		xew.writeAttribute("min", cei.isRequired() ? "1" : "0");
		xew.writeAttribute("max", cei.isList() ? "unbounded" : "1");
		
		handleDoc(cei.getE());
		
		for (ElementInfo ei : main.getChildElementDeclarations().get(cei.getTypeDeclaration()).getElementInfo()) {
			xew.writeStartElement("possibility");
			xew.writeAttribute("refId", "element-" + getId(ei.getXSDTypeName(m)));
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
		handleDoc(ai.getE());
		xew.writeEndElement();
	}

	private void handleDoc(Element element) throws XMLStreamException {
		String javadoc = processingEnv.getElementUtils().getDocComment(element);
		if (javadoc == null)
			return;
		
		Doc doc = new Doc(javadoc);
		
		xew.writeStartElement("documentation");
		
		for (Doc.Entry e : doc.getEntries())
			handleDoc(e);
		
		xew.writeEndElement();
	}
	
	private void handleDoc(Doc.Entry e) throws XMLStreamException {
		xew.writeStartElement(e.getKey());
		xew.writeCharacters(e.getValueAsXMLSnippet());
		xew.writeEndElement();
	}

}
