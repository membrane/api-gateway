package com.predic8.membrane.annot.model.doc;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

public class Doc {

	final ProcessingEnvironment processingEnv;
	final Element e;
	
	public class Entry {
		String key;
		String value;

		public Entry(String key, String value) {
			super();
			this.key = key;
			this.value = value;
		}
		
		public String getKey() {
			return key;
		}
		
		public String getValueAsXMLSnippet() {
			return value;
		}
	}
	
	Map<String, Entry> entries = new HashMap<String, Entry>();
	
	List<String> POSITIVE = Arrays.asList("description", "example", "default", "explanation");
	List<String> NEGATIVE = Arrays.asList("author", "param");
	
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
		
		entries.put(key, new Entry(key, value));
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
	}
	
	public Collection<Entry> getEntries() {
		return entries.values();
	}

}
