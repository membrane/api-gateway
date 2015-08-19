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
package com.predic8.membrane.annot.parser;

import java.util.HashMap;
import java.util.Set;

import org.apache.aries.blueprint.ParserContext;
import org.apache.aries.blueprint.mutable.MutableBeanMetadata;
import org.apache.aries.blueprint.mutable.MutableCollectionMetadata;
import org.apache.aries.blueprint.mutable.MutablePassThroughMetadata;
import org.apache.aries.blueprint.mutable.MutableRefMetadata;
import org.apache.aries.blueprint.mutable.MutableValueMetadata;
import org.osgi.service.blueprint.reflect.BeanMetadata;
import org.osgi.service.blueprint.reflect.BeanProperty;
import org.osgi.service.blueprint.reflect.Metadata;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.predic8.membrane.annot.MCElement;

/**
 * Base class for auto-generated blueprint parsers for {@link MCElement}s.
 */
public abstract class BlueprintElementParser implements BlueprintParser {

	private boolean inlined;

	public boolean isInlined() {
		return inlined;
	}

	public Metadata parseChild(BlueprintParser globalParser, Element element,
			ParserContext context) {
		boolean oldInlined = inlined;
		inlined = true;
		try {
			return parse(globalParser, element, context);
		} finally {
			inlined = oldInlined;
		}
	}

	protected void applySpringInterfacePatches(ParserContext context, Class<?> clazz, MutableBeanMetadata mcm) {
		if (ApplicationContextAware.class.isAssignableFrom(clazz)) {
			MutableRefMetadata mirm = context.createMetadata(MutableRefMetadata.class);
			mirm.setComponentId("blueprintContainer");

			MutableBeanMetadata helper = context.createMetadata(MutableBeanMetadata.class);
			helper.setId(context.generateId());
			helper.setScope(BeanMetadata.SCOPE_SINGLETON);
			helper.setRuntimeClass(BlueprintSpringInterfaceHelper.class);
			helper.addProperty("blueprintContainer", mirm);
			context.getComponentDefinitionRegistry().registerComponentDefinition(helper);

			// this does not work:
			//   mcm.addProperty("applicationContext", simulatedSpringApplicationContext);
			// -- the "applicationContext" property could possibly be set before any of the other properties (as they
			// are filled in using a HashMap), which differs from the Spring logic.

			// workaround:
			helper.addProperty("client", mcm);
			helper.setInitMethod("init");
			helper.setDestroyMethod("destroy");

			mcm.addDependsOn(helper.getId());

			return; // Lifecycle is handled by the Helper as well
		}

		if (Lifecycle.class.isAssignableFrom(clazz)) {
			mcm.setInitMethod("start");
			mcm.setDestroyMethod("stop");
		}
	}

	protected void parseChildren(Element element, ParserContext context,
			MutableBeanMetadata mcm, BlueprintParser globalParser) {
		element.setUserData(BlueprintNamespaceParser.KEY_PARENT_CLASS_NAME, mcm.getRuntimeClass().getName(), null);

		NodeList nl = element.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (node instanceof Element) {
				handleChildElement((Element) node, context, mcm, globalParser);
			}
		}
	}

	protected void setIdIfNeeded(Element element, ParserContext context,
			String defaultId) {
		if ( !isInlined() && !element.hasAttribute("id") ) {
			Set<String> names = context.getComponentDefinitionRegistry().getComponentDefinitionNames();
			for (int i = 0; ; i++) {
				String id = defaultId + (i == 0 ? "" : i);
				if (!names.contains(id)) {
					element.setAttribute("id", id);
					return;
				}
			}
		}
	}

	protected abstract void handleChildObject(Element ele, ParserContext parserContext, MutableBeanMetadata mcm, Class<?> clazz, Object child);


	protected void handleChildElement(Element ele, ParserContext context, MutableBeanMetadata mcm, BlueprintParser globalParser) {
		Metadata m = globalParser.parse(globalParser, ele, context);

		Class<?> clazz = null;
		if (m instanceof MutableBeanMetadata) {
			clazz = ((MutableBeanMetadata) m).getRuntimeClass();
		} else {
			throw new RuntimeException("Don't know how to get bean class from " + m.getClass() + ": " + ele.getNodeName());
		}

		handleChildObject(ele, context, mcm, clazz, m);
	}

	protected void setPropertyIfSet(ParserContext context, String xmlPropertyName, String springPropertyName,
			Element element, MutableBeanMetadata mcm) {
		setPropertyIfSet(context, xmlPropertyName, springPropertyName, element, mcm, false);
	}

	private BeanProperty findProperty(MutableBeanMetadata mcm, String springPropertyName) {
		for (BeanProperty p : mcm.getProperties())
			if (p.getName().equals(springPropertyName))
				return p;
		return null;
	}

	protected boolean isPropertySet(MutableBeanMetadata mcm, String springPropertyName) {
		return findProperty(mcm, springPropertyName) != null;
	}

	protected void setProperty(ParserContext context, String xmlPropertyName, String springPropertyName, Element element,
			MutableBeanMetadata mcm) {
		setProperty(context, xmlPropertyName, springPropertyName, element, mcm, false);
	}

	protected void setPropertyIfSet(ParserContext context, String prop, Element element,
			MutableBeanMetadata mcm) {
		setPropertyIfSet(context, prop, prop, element, mcm);
	}

	protected void appendToListProperty(ParserContext context, MutableBeanMetadata mcm, String springPropertyName, Object child) {
		MutableCollectionMetadata proxies = null;
		BeanProperty bp = findProperty(mcm, springPropertyName);
		if (bp == null) {
			proxies = context.createMetadata(MutableCollectionMetadata.class);
			mcm.addProperty(springPropertyName, proxies);
		} else {
			proxies = (MutableCollectionMetadata) bp.getValue();
		}
		proxies.addValue((Metadata)child);
	}

	protected void setProperty(ParserContext context, String xmlPropertyName, String springPropertyName,
			Element element, MutableBeanMetadata mcm, boolean flexibleEnum) {
		String value = element.getAttribute(xmlPropertyName);
		if (flexibleEnum)
			value = value.toUpperCase();
		MutableValueMetadata vm = context.createMetadata(MutableValueMetadata.class);
		vm.setStringValue(value);
		mcm.addProperty(springPropertyName, vm);
	}


	protected void setPropertyIfSet(ParserContext context, String xmlPropertyName, String springPropertyName,
			Element element, MutableBeanMetadata mcm, boolean flexibleEnum) {
		if (element.hasAttribute(xmlPropertyName))
			setProperty(context, xmlPropertyName, springPropertyName, element, mcm, flexibleEnum);
	}

	protected void setProperties(ParserContext context, String springPropertyName, Element element,
			MutableBeanMetadata mcm) {
		NamedNodeMap attributes = element.getAttributes();
		HashMap<String, String> attrs = new HashMap<String, String>();
		for (int i = 0; i < attributes.getLength(); i++) {
			Attr item = (Attr) attributes.item(i);
			if (item.getLocalName() != null)
				attrs.put(item.getLocalName(), item.getValue());
		}
		MutablePassThroughMetadata pt = context.createMetadata(MutablePassThroughMetadata.class);
		pt.setObject(attrs);
		mcm.addProperty(springPropertyName, pt);
	}

	protected void setProperty(ParserContext context, MutableBeanMetadata mcm, String springPropertyName, Object value) {
		if (value instanceof BeanMetadata) {
			mcm.addProperty(springPropertyName, (Metadata) value);
		} else {
			MutablePassThroughMetadata vm = context.createMetadata(MutablePassThroughMetadata.class);
			vm.setObject(value);
			mcm.addProperty(springPropertyName, vm);
		}
	}

	protected void setPropertyReference(ParserContext context, String springPropertyName, String beanId,
			MutableBeanMetadata mcm) {
		throw new RuntimeException("not implemented");
		//builder.addPropertyReference(springPropertyName, attributeValue);
	}


}

