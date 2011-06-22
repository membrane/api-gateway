package com.predic8.membrane.core.util;

import java.util.*;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;

/*
 * This implementation does not support using more multiple 
 */
public class MapNamespaceContext implements NamespaceContext {

	private Map<String, String> namespaces;
	
	public MapNamespaceContext(Map<String, String> namespaces) {
		this.namespaces = namespaces;
		addDefaultNamespaces();
	}
	
	@Override
	public String getNamespaceURI(String prefix) {
		if (prefix == null) {
			throw new IllegalArgumentException();
		}
		
		if (namespaces.containsKey(prefix)) {
			return namespaces.get(prefix);
		}
		return XMLConstants.NULL_NS_URI;
	}

	@Override
	public String getPrefix(String namespaceURI) {
		if (namespaceURI == null) {
			throw new IllegalArgumentException();
		}
		for (Map.Entry<String, String> e : namespaces.entrySet()) {
			if (e.getValue().equals(namespaceURI)) return e.getKey();
		}
		return null;
	}

	@Override
	public Iterator getPrefixes(String namespaceURI) {
		if (namespaceURI == null) {
			throw new IllegalArgumentException();
		}

		List<String> l = new ArrayList<String>();
		for (Map.Entry<String, String> e : namespaces.entrySet()) {
			if (e.getValue().equals(namespaceURI)) 
				l.add(e.getKey());
		}
		
		return l.iterator();
	}			
	
	private void addDefaultNamespaces() {
		namespaces.put(XMLConstants.XML_NS_PREFIX, XMLConstants.XML_NS_URI);
		namespaces.put(XMLConstants.XMLNS_ATTRIBUTE, XMLConstants.XMLNS_ATTRIBUTE_NS_URI);
		if (!namespaces.containsKey(XMLConstants.DEFAULT_NS_PREFIX)) {
			namespaces.put(XMLConstants.DEFAULT_NS_PREFIX, XMLConstants.NULL_NS_URI);
		}
	}
	
}
