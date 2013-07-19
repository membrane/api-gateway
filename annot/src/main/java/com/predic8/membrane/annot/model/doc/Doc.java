package com.predic8.membrane.annot.model.doc;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Doc {

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
	
	public Doc(String javadoc) {
		Matcher m = Pattern.compile("@(\\w+)\\s+(([^@]|@\\W)*)").matcher(javadoc);
		while (m.find()) {
			String value = m.group(2).trim();
			String key = m.group(1);
			
			if (!"description".equals(key) &&
				!"example".equals(key) &&
				!"default".equals(key)
					)
				continue;
			
			entries.put(key, new Entry(key, value));
		}
	}
	
	public Collection<Entry> getEntries() {
		return entries.values();
	}

}
