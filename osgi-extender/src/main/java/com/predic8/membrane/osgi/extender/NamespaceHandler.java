/* Copyright 2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.osgi.extender;

import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.aries.blueprint.ParserContext;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.osgi.service.blueprint.reflect.Metadata;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.predic8.membrane.annot.NamespaceUtil;
import com.predic8.membrane.annot.parser.BlueprintParser;
import com.predic8.membrane.core.Router;

/**
 * An OSGi service extending the OSGi container's blueprint parser to support
 * Membrane's custom configuration XML elements.
 */
public class NamespaceHandler implements org.apache.aries.blueprint.NamespaceHandler, BlueprintParser {

	NamespaceUtil nu = new NamespaceUtil();

	public ComponentMetadata decorate(Node arg0, ComponentMetadata arg1,
			ParserContext arg2) {
		throw new IllegalStateException("decorate() should never be called (as there are no attributes defined in the membrane namespace).");
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Set<Class> getManagedClasses() {
		return new HashSet<Class>(Arrays.asList(
				Router.class
				));
	}

	public URL getSchemaLocation(String namespace) {
		try {
			for (String tns : nu.getTargetNamespaces()) {
				if (namespace.equals(tns))
					return Class.forName(nu.getOutputPackage(tns) + ".NamespaceHandler").getResource(nu.getOutputName(tns));
			}
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	public Metadata parse(Element element, ParserContext context) {
		return parse(this, element, context);
	}

	@Override
	public Metadata parse(BlueprintParser globalParser, Element element,
			ParserContext context) {

		try {
			Class<?> clazz = Class.forName(nu.getOutputPackage(element.getNamespaceURI()) + ".blueprint.BlueprintNamespaceParser");

			BlueprintParser bp = (BlueprintParser) clazz.newInstance();

			return bp.parse(this, element, context);

		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

}
