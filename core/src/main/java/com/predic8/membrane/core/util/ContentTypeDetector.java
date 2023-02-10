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

import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import com.google.common.collect.ImmutableSet;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.multipart.XOPReconstitutor;

import static com.predic8.membrane.core.http.MimeType.*;

/**
 * This class tries to detect the "content type" of a given message.
 * "Content Type" here is more complex than the HTTP header "Content-Type": For
 * example a message of the effective type "SOAP" might be XOP-encoded and have
 * HTTP "Content-Type" "multipart/related".
 * Note that this class does not give a guarantee that the content is actually
 * valid.
 */
public class ContentTypeDetector {
	public enum ContentType {
		SOAP,
		XML,
		JSON,

		UNKNOWN
	}

	public static class ContentDescriptor {
		private final ContentType effectiveContentType;

		public ContentDescriptor(ContentType effectiveContentType) {
			this.effectiveContentType = effectiveContentType;
		}

		/**
		 * @return the contentType this message effectively is (e.g. return
		 *         "SOAP", if the message is a multipart/related XOP-encoded
		 *         SOAP-message).
		 */
		public ContentType getEffectiveContentType() {
			return effectiveContentType;
		}
	}


	private static final Set<String> contentTypesXML = ImmutableSet.of(
			"text/xml",
			"application/xml",
			"multipart/related");

	private static final Set<String> contentTypesJSON = ImmutableSet.of(
			APPLICATION_JSON,
			APPLICATION_X_JAVASCRIPT,
			TEXT_JAVASCRIPT,
			TEXT_X_JAVASCRIPT,
			TEXT_X_JSON);

	private static final XOPReconstitutor xopr = new XOPReconstitutor();
	private static final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
	static {
		xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
	}

	public static ContentDescriptor detect(Message m) {
		try {
			javax.mail.internet.ContentType t = m.getHeader().getContentTypeObject();
			if (t == null)
				return new ContentDescriptor(ContentType.UNKNOWN);

			String type = t.getPrimaryType() + "/" + t.getSubType();

			// JSON
			if (contentTypesJSON.contains(type))
				return new ContentDescriptor(ContentType.JSON);

			// XML
			if (contentTypesXML.contains(type)) {
				XMLStreamReader reader;
				synchronized(xmlInputFactory) {
					reader = xmlInputFactory.createXMLStreamReader(xopr.reconstituteIfNecessary(m));
				}
				if (reader.nextTag() == XMLStreamReader.START_ELEMENT) {
					boolean isSOAP =
							Constants.SOAP11_NS.equals(reader.getNamespaceURI()) ||
							Constants.SOAP12_NS.equals(reader.getNamespaceURI());
					if (isSOAP)
						return new ContentDescriptor(ContentType.SOAP);
					return new ContentDescriptor(ContentType.XML);
				}
			}

		} catch (Exception e) {
			// do nothing
		}
		return new ContentDescriptor(ContentType.UNKNOWN);
	}

}
