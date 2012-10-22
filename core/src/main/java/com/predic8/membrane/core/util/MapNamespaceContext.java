/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

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
	public Iterator<String> getPrefixes(String namespaceURI) {
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
