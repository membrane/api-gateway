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
package com.predic8.membrane.annot.bean;

import com.predic8.membrane.annot.*;
import org.slf4j.*;
import org.springframework.beans.*;
import org.springframework.context.support.*;
import org.springframework.core.io.*;

import javax.xml.stream.*;
import java.io.*;
import java.lang.reflect.*;
import java.security.*;
import java.util.*;

import static java.nio.charset.StandardCharsets.*;

/**
 * A utility class to deeply-clone/serizalize/deserialize {@link MCElement}-annotatated objects
 * (from/to a Spring-based XML configuration file).
 * <p>
 * The serialization process may fail: This occurs when non-{@link MCElement}-annotated objects are contained
 * in the object tree. This is, for example, the case in the JDBC logging example, where the DataSource is a
 * spring bean *not* created using {@link MCElement} annotations.
 * <p>
 * In case of a serialization failure, the resuling XML cannot be used to reconstruct the object tree.
 */
public class MCUtil {

	private static final Logger log = LoggerFactory.getLogger(MCUtil.class.getName());

	private static final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newFactory();

	@SuppressWarnings("unchecked")
	private static <T> T cloneInternal(T object, boolean deep) {
		if (object == null)
			return null;
		if (object instanceof Collection) {
			ArrayList<Object> res = new ArrayList<>(((Collection<?>) object).size());
			for (Object item : (Collection<?>)object)
				res.add(deep ? cloneInternal(item, deep) : item);
			return (T) res;
		}
		return clone(object, deep);
	}

	@SuppressWarnings("unchecked")
	public static <T> T clone(T object, boolean deep) {
		try {
			if (object == null)
				throw new InvalidParameterException("'object' must not be null.");

			Class<?> clazz = object.getClass();

			MCElement e = clazz.getAnnotation(MCElement.class);
			if (e == null)
				throw new IllegalArgumentException("'object' must be @MCElement-annotated.");

			BeanWrapperImpl dst = new BeanWrapperImpl(clazz);
			BeanWrapperImpl src = new BeanWrapperImpl(object);

			for (Method m : clazz.getMethods()) {
				if (!m.getName().startsWith("set"))
					continue;
				String propertyName = AnnotUtils.dejavaify(m.getName().substring(3));
				MCAttribute a = m.getAnnotation(MCAttribute.class);
				if (a != null) {
					dst.setPropertyValue(propertyName, src.getPropertyValue(propertyName));
				}
				MCChildElement c = m.getAnnotation(MCChildElement.class);
				if (c != null) {
					if (deep) {
						dst.setPropertyValue(propertyName, cloneInternal(src.getPropertyValue(propertyName), deep));
					} else {
						dst.setPropertyValue(propertyName, src.getPropertyValue(propertyName));
					}
				}
				MCOtherAttributes o = m.getAnnotation(MCOtherAttributes.class);
				if (o != null) {
					dst.setPropertyValue(propertyName, src.getPropertyValue(propertyName));
				}
				MCTextContent t = m.getAnnotation(MCTextContent.class);
				if (t != null) {
					dst.setPropertyValue(propertyName, src.getPropertyValue(propertyName));
				}
			}


			return (T) dst.getRootInstance();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T fromXML(Class<T> clazz, final String xml) {
		final String MAGIC = "magic.xml";

		FileSystemXmlApplicationContext fsxacApplicationContext = new FileSystemXmlApplicationContextExtension(MAGIC, xml);
		fsxacApplicationContext.setConfigLocation(MAGIC);

		try {
			fsxacApplicationContext.refresh();
		} catch (RuntimeException e) {
			log.error(e.getMessage(), e);
			System.err.println(xml);
			throw e;
		}

		Object bean = null;

		if (fsxacApplicationContext.containsBean("main")) {
			bean = fsxacApplicationContext.getBean("main");
		} else {
			Collection<T> beans = fsxacApplicationContext.getBeansOfType(clazz).values();
			if (beans.size() > 1)
				throw new InvalidParameterException("There is more than one bean of type '" + clazz.getName() + "'.");
			bean = beans.iterator().next();
		}

		if (bean == null)
			throw new InvalidParameterException("Did not find bean with ID 'main'.");

		if (!clazz.isAssignableFrom(bean.getClass()))
			throw new InvalidParameterException("Bean 'main' is not a " + clazz.getName() + " .");

		return (T) bean;
	}

	private static final class FileSystemXmlApplicationContextExtension extends FileSystemXmlApplicationContext {
		private final String MAGIC;
		private final String xml;

		private FileSystemXmlApplicationContextExtension(String MAGIC, String xml) {
			this.MAGIC = MAGIC;
			this.xml = xml;
		}

		@Override
		public Resource getResource(String location) {
			if (MAGIC.equals(location)) {
				return new FileSystemResource(MAGIC) {
					@Override
					public InputStream getInputStream() {
						return new ByteArrayInputStream(xml.getBytes(UTF_8));
					}
				};
			}
			return super.getResource(location);
		}
	}

	private static class SerializationContext {
		boolean incomplete;
		HashMap<String, String> beans = new HashMap<>();
		HashMap<Object, String> ids = new HashMap<>();
		int nextBean = 1;

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();

			sb.append("<spring:beans xmlns=\"http://membrane-soa.org/proxies/1/\"\r\n");
			sb.append("  xmlns:spring=\"http://www.springframework.org/schema/beans\"\r\n");
			sb.append("  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n");
			sb.append("  xsi:schemaLocation=\"http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.2.xsd\r\n");
			sb.append("    http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd\">\r\n");
			sb.append("\r\n");

			if (incomplete)
				sb.append("<!-- WARNING: This is an incomplete serialization of Membrane's configuration. Only use this config as a template for further processing. -->\r\n");

			for (String def : beans.values()) {
				sb.append(def);
				sb.append("\r\n");
			}

			sb.append("\r\n");
			sb.append("</spring:beans>\r\n");
			return sb.toString();
		}
	}

	private static void addXML(Object object, String id, XMLStreamWriter xew, SerializationContext sc) throws XMLStreamException {
		if (object == null)
			throw new InvalidParameterException("'object' must not be null.");

		Class<?> clazz = object.getClass();

		MCElement e = clazz.getAnnotation(MCElement.class);
		if (e == null)
			throw new IllegalArgumentException("'object' must be @MCElement-annotated.");

		BeanWrapperImpl src = new BeanWrapperImpl(object);

		xew.writeStartElement(e.name());

		if (id != null)
			xew.writeAttribute("id", id);

		HashSet<String> attributes = new HashSet<>();
		for (Method m : clazz.getMethods()) {
			if (!m.getName().startsWith("set"))
				continue;
			String propertyName = AnnotUtils.dejavaify(m.getName().substring(3));
			MCAttribute a = m.getAnnotation(MCAttribute.class);
			if (a != null) {
				Object value = src.getPropertyValue(propertyName);
				String str;
                switch (value) {
                    case null -> {
                        continue;
                    }
                    case String s -> str = s;
                    case Boolean b -> str = b.toString();
                    case Integer i -> str = i.toString();
                    case Long l -> str = l.toString();
                    case Enum<?> anEnum -> str = value.toString();
                    default -> {
                        MCElement el = value.getClass().getAnnotation(MCElement.class);
                        if (el != null) {
                            str = defineBean(sc, value, null, true);
                        } else {
                            str = "?";
                            sc.incomplete = true;
                        }
                    }
                }

				if (!a.attributeName().isEmpty())
					propertyName = a.attributeName();

				attributes.add(propertyName);
				xew.writeAttribute(propertyName, str);
			}
		}
		for (Method m : clazz.getMethods()) {
			if (!m.getName().startsWith("set"))
				continue;
			String propertyName = AnnotUtils.dejavaify(m.getName().substring(3));
			MCOtherAttributes o = m.getAnnotation(MCOtherAttributes.class);
			if (o != null) {
				Object value = src.getPropertyValue(propertyName);
				if (value instanceof Map<?, ?> map) {
                    for (Map.Entry<?,?> entry : map.entrySet()) {
						Object key = entry.getKey();
						Object val = entry.getValue();
						if (!(key instanceof String) || !(val instanceof String)) {
							sc.incomplete = true;
							key = "incompleteAttributes";
							val = "?";
						}
						if (attributes.contains(key))
							continue;
						attributes.add((String)key);
						xew.writeAttribute((String)key, (String)val);
					}
				} else {
					xew.writeAttribute("incompleteAttributes", "?");
					sc.incomplete = true;
				}
			}
		}

		List<Method> childElements = new ArrayList<>();
		for (Method m : clazz.getMethods()) {
			if (!m.getName().startsWith("set"))
				continue;
			String propertyName = AnnotUtils.dejavaify(m.getName().substring(3));

			MCChildElement c = m.getAnnotation(MCChildElement.class);
			if (c != null) {
				childElements.add(m);
			}
			MCTextContent t = m.getAnnotation(MCTextContent.class);
			if (t != null) {
				Object value = src.getPropertyValue(propertyName);
				if (value == null) {
					continue;
				} else if (value instanceof String v) {
					xew.writeCharacters(v);
				} else {
					xew.writeCharacters("?");
					sc.incomplete = true;
				}
			}
		}

		childElements.sort((o1, o2) -> {
			MCChildElement c1 = o1.getAnnotation(MCChildElement.class);
			MCChildElement c2 = o2.getAnnotation(MCChildElement.class);
			return c1.order() - c2.order();
		});

		for (Method m : childElements) {
			String propertyName = AnnotUtils.dejavaify(m.getName().substring(3));

			Object value = src.getPropertyValue(propertyName);
			if (value != null) {
				if (value instanceof Collection<?> col) {
					for (Object item : col)
						addXML(item, null, xew, sc);
				} else {
					addXML(value, null, xew, sc);
				}
			}
		}


		xew.writeEndElement();
	}

	private static String defineBean(SerializationContext sc, Object object, String idSuggestion, boolean requireBeanId) throws XMLStreamException {
		if (sc.ids.containsKey(object))
			return sc.ids.get(object);

		String id = idSuggestion;

		if (requireBeanId && id == null)
			id = "bean" + sc.nextBean++;

		StringWriter sw = new StringWriter();
		XMLStreamWriter xew = xmlOutputFactory.createXMLStreamWriter(sw);

		addXML(object, id, xew, sc);
		xew.flush();

		if (id == null)
			id = "bean" + sc.nextBean++;

		sc.beans.put(id, sw.toString());
		sc.ids.put(object, id);

		return id;
	}

	public static String toXML(Object object) {
		try {

			SerializationContext sc = new SerializationContext();

			defineBean(sc, object, "main", true);

			return sc.toString();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
