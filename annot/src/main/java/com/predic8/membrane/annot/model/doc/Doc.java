package com.predic8.membrane.annot.model.doc;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EntityReference;
import javax.xml.stream.events.XMLEvent;

public class Doc {

	static final XMLInputFactory fac = XMLInputFactory.newFactory();
	
	final ProcessingEnvironment processingEnv;
	final Element e;
	
	public class Entry implements Comparable<Entry> {
		String key;
		String value;

		public Entry(String key, String value) {
			super();
			this.key = key;
			this.value = value;
			
			try {
				XMLEventReader xer = fac.createXMLEventReader(new StringReader(wrapInRootElement(value)));
				while (xer.hasNext()) {
					XMLEvent event = xer.nextEvent();
					if (event.isEntityReference()) {
						EntityReference er = (EntityReference) event;
						processingEnv.getMessager().printMessage(Kind.ERROR, "Entity " + er.getName() + " found, but not allowed.", e);
					}
				}
			} catch (XMLStreamException f) {
				value = "";
				processingEnv.getMessager().printMessage(Kind.WARNING, f.getMessage(), e);
			}
		}

		private String wrapInRootElement(String value) {
			return "<" + key + ">" + value + "</" + key + ">";
		}
		
		public String getKey() {
			return key;
		}
		
		
		/**
		 * @return a string containing a valid XML node set (or the empty string, if the input was invalid and a
		 *         compiler warning was issued)
		 */
		public String getValueAsXMLSnippet(boolean wrap) {
			if (wrap)
				return wrapInRootElement(value);
			else
				return value;
		}

		@Override
		public int compareTo(Entry o) {
			return POSITIVE.indexOf(key) - POSITIVE.indexOf(o.key);
		}
	}
	
	HashSet<String> keys = new HashSet<String>();
	List<Entry> entries = new ArrayList<Entry>();
	
	static final List<String> POSITIVE = Arrays.asList("topic", "description", "example", "default", "explanation");
	static final List<String> NEGATIVE = Arrays.asList("author", "param");
	
	private void handle(String key, String value) {
		value = value.trim();
		
		if (value.length() == 0)
			return;
		
		if (NEGATIVE.contains(key))
			return;

		if (!POSITIVE.contains(key)) {
			processingEnv.getMessager().printMessage(Kind.WARNING, "Unknown javadoc tag: " + key, e);
			return;
		}
		
		if (keys.contains(key)) {
			processingEnv.getMessager().printMessage(Kind.WARNING, "Duplicate JavaDoc tag: " + key, e);
			return;
		}
		
		keys.add(key);
		entries.add(new Entry(key, value));
	}		
	
	public Doc(ProcessingEnvironment processingEnv, String javadoc, Element e) {
		this.processingEnv = processingEnv;
		this.e = e;
		Matcher m = Pattern.compile("(?:^|[^{])@(\\w+)").matcher(javadoc);
		int last = -1;
		String key = null;
		while (m.find()) {
			if (last != -1)
				handle(key, javadoc.substring(last+1, m.start(1)-1));
			key = m.group(1);
			last = m.end(1);
		}
		if (last != -1)
			handle(key, javadoc.substring(last));
		
		Collections.sort(entries);
	}
	
	public List<Entry> getEntries() {
		return entries;
	}

}
